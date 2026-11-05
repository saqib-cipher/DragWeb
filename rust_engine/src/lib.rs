use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use rustc_hash::FxHashMap;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct DragWebBlockBean {
    pub id: String,
    #[serde(rename = "opCode", alias = "op_code")]
    pub op_code: String,
    pub spec: String,
    #[serde(default)]
    pub spec2: String,
    #[serde(rename = "type", alias = "block_type")]
    pub block_type: String,
    #[serde(default)]
    pub category: String,
    pub color: i32,
    #[serde(default)]
    pub code: String,
    #[serde(default)]
    pub parameters: Vec<String>,
    #[serde(rename = "paramTypes", alias = "param_types", default)]
    pub param_types: Vec<String>,
    #[serde(rename = "subStack1", alias = "sub_stack1")]
    pub sub_stack1: i32,
    #[serde(rename = "subStack2", alias = "sub_stack2")]
    pub sub_stack2: i32,
    #[serde(rename = "nextBlock", alias = "next_block")]
    pub next_block: i32,
    #[serde(rename = "stackIndex", alias = "stack_index", default)]
    pub stack_index: i32,
    #[serde(rename = "isConnectedToRoot", alias = "is_connected_to_root", default)]
    pub is_connected_to_root: bool,
    #[serde(default)]
    pub x: i32,
    #[serde(default)]
    pub y: i32,
}

#[derive(Clone, Debug)]
struct BlockDef {
    id: String,
    category: String,
    block_type: String,
    color: i32,
    spec: String,
    spec2: String,
    code: String,
}

#[derive(Clone, Debug)]
enum HtmlNode {
    Element {
        tag_name: String,
        attributes: Vec<(String, String)>,
        children: Vec<HtmlNode>,
        is_void: bool,
    },
    Text(String),
    Comment(String),
}

struct GeneratorContext {
    counter: i32,
    blocks: Vec<DragWebBlockBean>,
    tag_map: FxHashMap<String, BlockDef>,
    attr_map: FxHashMap<String, BlockDef>,
    fallback_container: BlockDef,
    fallback_leaf: BlockDef,
    fallback_attr_generic: BlockDef,
    fallback_text: BlockDef,
    fallback_comment: BlockDef,
}

impl GeneratorContext {
    fn next_id(&mut self) -> i32 {
        let id = self.counter;
        self.counter += 1;
        id
    }

    fn add_block(&mut self, block: DragWebBlockBean) {
        self.blocks.push(block);
    }

    fn set_next_block(&mut self, from_id: i32, to_id: i32) {
        let str_id = from_id.to_string();
        if let Some(b) = self.blocks.iter_mut().find(|b| b.id == str_id) {
            b.next_block = to_id;
        }
    }
}

const VOID_ELEMENTS: &[&str] = &[
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
    "source", "track", "wbr",
];

/// Parse a block spec string and extract the param type identifiers for each `%X` token.
/// Returns a Vec of param type strings, one per parameter slot in the spec.
/// Examples:
///   "%m.htmlInputType"  -> "m.htmlInputType"
///   "%s.inputOnly"      -> "s.inputOnly"
///   "%m.htmlTarget"     -> "m.htmlTarget"
///   "%n"                -> "n"
///   "%b"                -> "b"
///   "%d"                -> "d"
fn parse_spec_param_types(spec: &str) -> Vec<String> {
    let mut types = Vec::new();
    let chars: Vec<char> = spec.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        if chars[i] == '%' && i + 1 < chars.len() {
            i += 1;
            // Read the type character(s) and optional dotted name
            let mut token = String::new();
            while i < chars.len() && chars[i] != ' ' && chars[i] != '<' && chars[i] != '>' && chars[i] != '"' && chars[i] != '\'' && chars[i] != '/' {
                token.push(chars[i]);
                i += 1;
            }
            // Strip trailing punctuation
            let token = token.trim_end_matches(&[',', ';', ')', '(', ']', '['][..]);
            if !token.is_empty() {
                types.push(token.to_string());
            }
        } else {
            i += 1;
        }
    }
    types
}

fn parse_hex_color_i32(hex: &str) -> i32 {
    let s = hex.trim_start_matches('#');
    if s.len() == 6 {
        u32::from_str_radix(s, 16)
            .map(|rgb| (0xFF000000 | rgb) as i32)
            .unwrap_or(-10453621) // #607D8B default
    } else if s.len() == 8 {
        u32::from_str_radix(s, 16)
            .map(|rgba| rgba as i32)
            .unwrap_or(-10453621)
    } else {
        -10453621
    }
}

fn load_definitions(
    blocks_json: &str,
) -> (
    FxHashMap<String, BlockDef>,
    FxHashMap<String, BlockDef>,
    BlockDef,
    BlockDef,
    BlockDef,
    BlockDef,
    BlockDef,
) {
    let mut tag_map = FxHashMap::default();
    let mut attr_map = FxHashMap::default();

    let mut fb_cont = BlockDef {
        id: "html_convert".to_string(),
        category: "html".to_string(),
        block_type: "e".to_string(),
        color: parse_hex_color_i32("#607D8B"),
        spec: "<%s.inputOnly>".to_string(),
        spec2: "children".to_string(),
        code: "<%1$s%2$s>\n%3$s\n</%1$s>\n".to_string(),
    };

    let mut fb_leaf = BlockDef {
        id: "html_convert_leaf".to_string(),
        category: "html".to_string(),
        block_type: "c".to_string(),
        color: parse_hex_color_i32("#607D8B"),
        spec: "<%s.inputOnly />".to_string(),
        spec2: String::new(),
        code: "<%1$s%2$s />\n".to_string(),
    };

    let mut fb_attr = BlockDef {
        id: "htmlAttrGeneric".to_string(),
        category: "html_attr".to_string(),
        block_type: "normal".to_string(),
        color: parse_hex_color_i32("#78909C"),
        spec: "%s.inputOnly = %s.inputOnly".to_string(),
        spec2: String::new(),
        code: " %1$s=\"%2$s\"".to_string(),
    };

    let mut fb_text = BlockDef {
        id: "htmlText".to_string(),
        category: "html".to_string(),
        block_type: "normal".to_string(),
        color: parse_hex_color_i32("#607D8B"),
        spec: "text %s.inputOnly".to_string(),
        spec2: String::new(),
        code: "%1$s\n".to_string(),
    };

    let mut fb_comm = BlockDef {
        id: "htmlComment".to_string(),
        category: "html".to_string(),
        block_type: "normal".to_string(),
        color: parse_hex_color_i32("#607D8B"),
        spec: "<!-- %s.inputOnly -->".to_string(),
        spec2: String::new(),
        code: "<!-- %1$s -->\n".to_string(),
    };

    if !blocks_json.is_empty() {
        if let Ok(Value::Array(items)) = serde_json::from_str::<Value>(blocks_json) {
            for item in items {
                if let Some(obj) = item.as_object() {
                    let id = obj.get("id").and_then(|v| v.as_str()).unwrap_or("");
                    if id.is_empty() {
                        continue;
                    }
                    let category = obj.get("category").and_then(|v| v.as_str()).unwrap_or("");
                    let spec = obj.get("spec").and_then(|v| v.as_str()).unwrap_or("");
                    let spec2 = obj.get("spec2").and_then(|v| v.as_str()).unwrap_or("");
                    let block_type = obj
                        .get("blockType")
                        .or_else(|| obj.get("type"))
                        .and_then(|v| v.as_str())
                        .unwrap_or("normal");
                    let code = obj.get("code").and_then(|v| v.as_str()).unwrap_or("");
                    let color = obj
                        .get("color")
                        .and_then(|v| v.as_str())
                        .map(parse_hex_color_i32)
                        .unwrap_or_else(|| parse_hex_color_i32("#607D8B"));

                    let def = BlockDef {
                        id: id.to_string(),
                        category: category.to_string(),
                        block_type: block_type.to_string(),
                        color,
                        spec: spec.to_string(),
                        spec2: if !spec2.is_empty() {
                            spec2.to_string()
                        } else if block_type == "e" && (category.eq_ignore_ascii_case("html") || id.starts_with("html")) {
                            "children".to_string()
                        } else {
                            String::new()
                        },
                        code: code.to_string(),
                    };

                    if id.eq_ignore_ascii_case("html_convert") {
                        fb_cont = def.clone();
                    } else if id.eq_ignore_ascii_case("html_convert_leaf") {
                        fb_leaf = def.clone();
                    } else if id.eq_ignore_ascii_case("htmlAttrGeneric") {
                        fb_attr = def.clone();
                    } else if id.eq_ignore_ascii_case("htmlText") {
                        fb_text = def.clone();
                    } else if id.eq_ignore_ascii_case("htmlComment") {
                        fb_comm = def.clone();
                    }

                    if category.eq_ignore_ascii_case("html_attr") || id.starts_with("htmlAttr") {
                        let clean = id.replace("htmlAttr", "").to_lowercase();
                        if !clean.is_empty() {
                            attr_map.insert(clean, def.clone());
                        }
                    } else if category.eq_ignore_ascii_case("html") || id.starts_with("html") {
                        let clean = id.replace("html", "").to_lowercase();
                        if !clean.is_empty() {
                            tag_map.insert(clean, def.clone());
                        }
                    }
                }
            }
        }
    }

    // Standard tag aliases
    if let Some(p) = tag_map.get("paragraph").cloned() {
        tag_map.entry("p".to_string()).or_insert(p);
    }
    if let Some(a) = tag_map.get("anchor").cloned() {
        tag_map.entry("a".to_string()).or_insert(a);
    }
    if let Some(img) = tag_map.get("image").cloned() {
        tag_map.entry("img".to_string()).or_insert(img);
    }
    if let Some(ul) = tag_map.get("unorderedlist").cloned() {
        tag_map.entry("ul".to_string()).or_insert(ul);
    }
    if let Some(ol) = tag_map.get("orderedlist").cloned() {
        tag_map.entry("ol".to_string()).or_insert(ol);
    }
    if let Some(li) = tag_map.get("listitem").cloned() {
        tag_map.entry("li".to_string()).or_insert(li);
    }

    (tag_map, attr_map, fb_cont, fb_leaf, fb_attr, fb_text, fb_comm)
}

fn process_node(ctx: &mut GeneratorContext, node: &HtmlNode, is_root: bool) -> i32 {
    match node {
        HtmlNode::Comment(comment) => {
            let id = ctx.next_id();
            let b = DragWebBlockBean {
                id: id.to_string(),
                op_code: ctx.fallback_comment.id.clone(),
                spec: ctx.fallback_comment.spec.clone(),
                spec2: String::new(),
                block_type: ctx.fallback_comment.block_type.clone(),
                category: ctx.fallback_comment.category.clone(),
                color: ctx.fallback_comment.color,
                code: ctx.fallback_comment.code.clone(),
                parameters: vec![comment.clone()],
                param_types: vec!["s".to_string()],
                sub_stack1: -1,
                sub_stack2: -1,
                next_block: -1,
                stack_index: 0,
                is_connected_to_root: is_root,
                x: 0,
                y: 0,
            };
            ctx.add_block(b);
            id
        }
        HtmlNode::Text(text) => {
            let trimmed = text.trim();
            if trimmed.is_empty() {
                return -1;
            }
            let id = ctx.next_id();
            let b = DragWebBlockBean {
                id: id.to_string(),
                op_code: ctx.fallback_text.id.clone(),
                spec: ctx.fallback_text.spec.clone(),
                spec2: String::new(),
                block_type: ctx.fallback_text.block_type.clone(),
                category: ctx.fallback_text.category.clone(),
                color: ctx.fallback_text.color,
                code: ctx.fallback_text.code.clone(),
                parameters: vec![trimmed.to_string()],
                param_types: vec!["s".to_string()],
                sub_stack1: -1,
                sub_stack2: -1,
                next_block: -1,
                stack_index: 0,
                is_connected_to_root: is_root,
                x: 0,
                y: 0,
            };
            ctx.add_block(b);
            id
        }
        HtmlNode::Element {
            tag_name,
            attributes,
            children,
            is_void,
        } => {
            let tag_lower = tag_name.to_lowercase();
            let id = ctx.next_id();

            let matched_def = ctx.tag_map.get(&tag_lower).cloned();
            let (op_code, spec, spec2, b_type, cat, color, code, params, ptype_from_spec) = if let Some(def) = matched_def {
                // Extract param types from spec  
                let spec_types = parse_spec_param_types(&def.spec);
                // For each spec param, attempt to populate from known HTML attributes.
                // This fills inline block params (e.g. type on htmlInput, href+target on htmlAnchor).
                // The attribute will also appear in sub_stack1 as an attr block — both are fine.
                let attr_lookup: std::collections::HashMap<&str, &str> = attributes
                    .iter()
                    .map(|(k, v)| (k.as_str(), v.as_str()))
                    .collect();
                // Map of spec param types -> preferred attribute name to pull from
                let inline_attr_map: &[(&str, &[&str])] = &[
                    ("m.htmlInputType", &["type"]),
                    ("m.htmlTarget",    &["target"]),
                    ("m.htmlMethod",    &["method"]),
                    ("m.htmlButtonType",&["type"]),
                    ("m.htmlLoading",   &["loading"]),
                    ("m.htmlPreload",   &["preload"]),
                    ("m.htmlDecoding",  &["decoding"]),
                    ("m.htmlWrap",      &["wrap"]),
                    ("m.htmlDir",       &["dir"]),
                    ("m.htmlCrossorigin",&["crossorigin"]),
                    ("s.inputOnly",     &["href","src","value","action"]),
                ];
                let mut init_params: Vec<String> = Vec::with_capacity(spec_types.len());
                for st in &spec_types {
                    let mut found = String::new();
                    for (ptype, attr_names) in inline_attr_map {
                        if st == ptype {
                            for an in *attr_names {
                                if let Some(v) = attr_lookup.get(an) {
                                    found = v.to_string();
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    init_params.push(found);
                }
                (def.id, def.spec, def.spec2, def.block_type, def.category, def.color, def.code, init_params, spec_types)
            } else if *is_void {
                let fb_spec = ctx.fallback_leaf.spec.clone();
                let spec_types = parse_spec_param_types(&fb_spec);
                (
                    ctx.fallback_leaf.id.clone(),
                    fb_spec,
                    String::new(),
                    ctx.fallback_leaf.block_type.clone(),
                    ctx.fallback_leaf.category.clone(),
                    ctx.fallback_leaf.color,
                    ctx.fallback_leaf.code.clone(),
                    vec![tag_name.clone(), String::new()],
                    spec_types,
                )
            } else {
                let fb_spec = ctx.fallback_container.spec.clone();
                let spec_types = parse_spec_param_types(&fb_spec);
                (
                    ctx.fallback_container.id.clone(),
                    fb_spec,
                    "children".to_string(),
                    ctx.fallback_container.block_type.clone(),
                    ctx.fallback_container.category.clone(),
                    ctx.fallback_container.color,
                    ctx.fallback_container.code.clone(),
                    vec![tag_name.clone(), String::new()],
                    spec_types,
                )
            };

            // Process Attributes into sub_stack1
            let mut first_attr_id = -1;
            let mut prev_attr_id = -1;

            for (name, val) in attributes {
                let attr_lower = name.to_lowercase();
                let attr_id = ctx.next_id();

                let matched_attr = ctx.attr_map.get(&attr_lower).cloned();
                let attr_block = if let Some(def) = matched_attr {
                    let mut p = Vec::new();
                    let op_low = def.id.to_lowercase();
                    let is_bool = op_low.contains("disabled")
                        || op_low.contains("checked")
                        || op_low.contains("required")
                        || op_low.contains("readonly")
                        || op_low.contains("hidden")
                        || op_low.contains("autofocus")
                        || op_low.contains("multiple")
                        || op_low.contains("selected");

                    if !(is_bool && (val.is_empty() || val == name)) {
                        p.push(val.clone());
                    }

                    // Derive param_types from spec  
                    let pt = parse_spec_param_types(&def.spec);
                    // Ensure param_types len >= params len
                    let param_types = if pt.is_empty() {
                        p.iter().map(|_| "s".to_string()).collect()
                    } else {
                        pt
                    };

                    DragWebBlockBean {
                        id: attr_id.to_string(),
                        op_code: def.id,
                        spec: def.spec,
                        spec2: String::new(),
                        block_type: def.block_type,
                        category: def.category,
                        color: def.color,
                        code: def.code,
                        parameters: p,
                        param_types,
                        sub_stack1: -1,
                        sub_stack2: -1,
                        next_block: -1,
                        stack_index: 0,
                        is_connected_to_root: false,
                        x: 0,
                        y: 0,
                    }
                } else {
                    DragWebBlockBean {
                        id: attr_id.to_string(),
                        op_code: ctx.fallback_attr_generic.id.clone(),
                        spec: ctx.fallback_attr_generic.spec.clone(),
                        spec2: String::new(),
                        block_type: ctx.fallback_attr_generic.block_type.clone(),
                        category: ctx.fallback_attr_generic.category.clone(),
                        color: ctx.fallback_attr_generic.color,
                        code: ctx.fallback_attr_generic.code.clone(),
                        parameters: vec![name.clone(), val.clone()],
                        param_types: vec!["s".to_string(), "s".to_string()],
                        sub_stack1: -1,
                        sub_stack2: -1,
                        next_block: -1,
                        stack_index: 0,
                        is_connected_to_root: false,
                        x: 0,
                        y: 0,
                    }
                };

                if first_attr_id == -1 {
                    first_attr_id = attr_id;
                }
                if prev_attr_id != -1 {
                    ctx.set_next_block(prev_attr_id, attr_id);
                }
                prev_attr_id = attr_id;
                ctx.add_block(attr_block);
            }

            // Process Children into sub_stack2
            let mut first_child_id = -1;
            let mut prev_child_id = -1;

            if !*is_void {
                for child in children {
                    let child_id = process_node(ctx, child, false);
                    if child_id >= 0 {
                        if first_child_id == -1 {
                            first_child_id = child_id;
                        }
                        if prev_child_id != -1 {
                            ctx.set_next_block(prev_child_id, child_id);
                        }
                        prev_child_id = child_id;
                    }
                }
            }

            let view_block = DragWebBlockBean {
                id: id.to_string(),
                op_code,
                spec,
                spec2,
                block_type: b_type,
                category: cat,
                color,
                code,
                parameters: params,
                param_types: ptype_from_spec,
                sub_stack1: first_attr_id,
                sub_stack2: if *is_void { -1 } else { first_child_id },
                next_block: -1,
                stack_index: 0,
                is_connected_to_root: is_root,
                x: 0,
                y: 0,
            };

            ctx.add_block(view_block);
            id
        }
    }
}

pub fn generate_dragweb_blocks(html: &str, blocks_json: &str) -> Vec<DragWebBlockBean> {
    let (tag_map, attr_map, fb_cont, fb_leaf, fb_attr, fb_text, fb_comm) =
        load_definitions(blocks_json);

    let mut ctx = GeneratorContext {
        counter: 10,
        blocks: Vec::new(),
        tag_map,
        attr_map,
        fallback_container: fb_cont,
        fallback_leaf: fb_leaf,
        fallback_attr_generic: fb_attr,
        fallback_text: fb_text,
        fallback_comment: fb_comm,
    };

    let mut nodes = parse_html(html);

    fn find_body_or_html_children(nodes: &[HtmlNode]) -> Option<Vec<HtmlNode>> {
        for node in nodes {
            if let HtmlNode::Element { tag_name, children, .. } = node {
                if tag_name.eq_ignore_ascii_case("html") {
                    for ch in children {
                        if let HtmlNode::Element { tag_name: ch_tag, children: ch_children, .. } = ch {
                            if ch_tag.eq_ignore_ascii_case("body") {
                                return Some(ch_children.clone());
                            }
                        }
                    }
                    return Some(children.clone());
                } else if tag_name.eq_ignore_ascii_case("body") {
                    return Some(children.clone());
                }
            }
        }
        None
    }

    if let Some(body_children) = find_body_or_html_children(&nodes) {
        nodes = body_children;
    }

    let mut prev_root_id = -1;
    for node in &nodes {
        let id = process_node(&mut ctx, node, true);
        if id >= 0 {
            if prev_root_id != -1 {
                ctx.set_next_block(prev_root_id, id);
            }
            prev_root_id = id;
        }
    }

    ctx.blocks.sort_by(|a, b| {
        let id_a: i32 = a.id.parse().unwrap_or(0);
        let id_b: i32 = b.id.parse().unwrap_or(0);
        id_a.cmp(&id_b)
    });

    for (i, b) in ctx.blocks.iter_mut().enumerate() {
        b.stack_index = i as i32;
        b.is_connected_to_root = true;
    }

    ctx.blocks
}

// =============================================================================
// Fast HTML Tokenizer & Tree Builder
// =============================================================================

fn parse_html(html: &str) -> Vec<HtmlNode> {
    let mut parser = HtmlParser::new(html);
    parser.parse_nodes()
}

struct HtmlParser {
    chars: Vec<char>,
    pos: usize,
}

impl HtmlParser {
    fn new(input: &str) -> Self {
        Self {
            chars: input.chars().collect(),
            pos: 0,
        }
    }

    fn peek(&self) -> Option<char> {
        self.chars.get(self.pos).copied()
    }

    fn next_char(&mut self) -> Option<char> {
        let ch = self.chars.get(self.pos).copied();
        if ch.is_some() {
            self.pos += 1;
        }
        ch
    }

    fn starts_with(&self, s: &str) -> bool {
        let s_chars: Vec<char> = s.chars().collect();
        if self.pos + s_chars.len() > self.chars.len() {
            return false;
        }
        for (i, &sc) in s_chars.iter().enumerate() {
            if self.chars[self.pos + i] != sc {
                return false;
            }
        }
        true
    }

    fn parse_nodes(&mut self) -> Vec<HtmlNode> {
        let mut nodes = Vec::new();

        while self.pos < self.chars.len() {
            // Comment
            if self.starts_with("<!--") {
                self.pos += 4;
                let mut comment = String::new();
                while self.pos < self.chars.len() && !self.starts_with("-->") {
                    comment.push(self.next_char().unwrap());
                }
                if self.starts_with("-->") {
                    self.pos += 3;
                }
                nodes.push(HtmlNode::Comment(comment.trim().to_string()));
                continue;
            }

            // Doctype <!DOCTYPE ...> or <?xml ...?>
            if self.starts_with("<!") || self.starts_with("<?") {
                while self.pos < self.chars.len() && self.peek() != Some('>') {
                    self.pos += 1;
                }
                if self.peek() == Some('>') {
                    self.pos += 1;
                }
                continue;
            }

            // Closing tag
            if self.starts_with("</") {
                break;
            }

            // Opening tag
            if self.starts_with("<") {
                if let Some(elem) = self.parse_element() {
                    nodes.push(elem);
                }
                continue;
            }

            // Text content
            let mut text = String::new();
            while self.pos < self.chars.len() && self.peek() != Some('<') {
                text.push(self.next_char().unwrap());
            }
            if !text.trim().is_empty() {
                nodes.push(HtmlNode::Text(text));
            }
        }

        nodes
    }

    fn parse_element(&mut self) -> Option<HtmlNode> {
        if self.next_char() != Some('<') {
            return None;
        }

        self.skip_whitespace();
        let mut tag_name = String::new();
        while let Some(ch) = self.peek() {
            if ch.is_alphanumeric() || ch == '-' || ch == '_' {
                tag_name.push(ch);
                self.pos += 1;
            } else {
                break;
            }
        }

        if tag_name.is_empty() {
            return None;
        }

        let is_doctype = tag_name.eq_ignore_ascii_case("!doctype");

        let mut attributes = Vec::new();
        let mut is_self_closing = false;

        loop {
            self.skip_whitespace();
            match self.peek() {
                Some('/') => {
                    self.pos += 1;
                    self.skip_whitespace();
                    if self.peek() == Some('>') {
                        self.pos += 1;
                        is_self_closing = true;
                        break;
                    }
                }
                Some('>') => {
                    self.pos += 1;
                    break;
                }
                None => break,
                _ => {
                    let (name, val) = self.parse_attribute();
                    if !name.is_empty() {
                        attributes.push((name, val));
                    }
                }
            }
        }

        if is_doctype {
            return None;
        }

        let tag_lower = tag_name.to_lowercase();
        let is_void = VOID_ELEMENTS.contains(&tag_lower.as_str()) || is_self_closing;

        let mut children = Vec::new();
        if !is_void {
            children = self.parse_nodes();

            if self.starts_with("</") {
                self.pos += 2;
                self.skip_whitespace();
                let mut close_tag = String::new();
                while let Some(ch) = self.peek() {
                    if ch.is_alphanumeric() || ch == '-' || ch == '_' {
                        close_tag.push(ch);
                        self.pos += 1;
                    } else {
                        break;
                    }
                }
                while let Some(ch) = self.peek() {
                    self.pos += 1;
                    if ch == '>' {
                        break;
                    }
                }
            }
        }

        Some(HtmlNode::Element {
            tag_name,
            attributes,
            children,
            is_void,
        })
    }

    fn parse_attribute(&mut self) -> (String, String) {
        let mut name = String::new();
        while let Some(ch) = self.peek() {
            if ch.is_alphanumeric() || ch == '-' || ch == '_' || ch == ':' {
                name.push(ch);
                self.pos += 1;
            } else {
                break;
            }
        }

        self.skip_whitespace();
        let mut val = String::new();

        if self.peek() == Some('=') {
            self.pos += 1;
            self.skip_whitespace();

            match self.peek() {
                Some('"') => {
                    self.pos += 1;
                    while let Some(ch) = self.peek() {
                        self.pos += 1;
                        if ch == '"' {
                            break;
                        }
                        val.push(ch);
                    }
                }
                Some('\'') => {
                    self.pos += 1;
                    while let Some(ch) = self.peek() {
                        self.pos += 1;
                        if ch == '\'' {
                            break;
                        }
                        val.push(ch);
                    }
                }
                Some(ch) if !ch.is_whitespace() && ch != '>' && ch != '/' => {
                    while let Some(c) = self.peek() {
                        if c.is_whitespace() || c == '>' || c == '/' {
                            break;
                        }
                        val.push(c);
                        self.pos += 1;
                    }
                }
                _ => {}
            }
        }

        (name, val)
    }

    fn skip_whitespace(&mut self) {
        while let Some(ch) = self.peek() {
            if ch.is_whitespace() {
                self.pos += 1;
            } else {
                break;
            }
        }
    }
}

// =============================================================================
// JNI Export for DragWeb
// =============================================================================

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_glab_dragweb_core_BlocksLoader_nativeGenerateHtmlBlocks(
    mut env: JNIEnv,
    _class: JClass,
    html_code: JString,
    blocks_json: JString,
) -> jstring {
    let binding_code = match env.get_string(&html_code) {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Failed to read html string: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };
    let code_str = match binding_code.to_str() {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Invalid UTF-8 in html: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };

    let binding_json = env.get_string(&blocks_json).ok();
    let json_str = binding_json.as_ref().and_then(|s| s.to_str().ok()).unwrap_or("");

    let blocks = generate_dragweb_blocks(code_str, json_str);
    let json_result = serde_json::to_string(&blocks).unwrap_or_else(|_| "[]".to_string());

    env.new_string(json_result).unwrap().into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_full_html() {
        let html = r#"<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>DragWeb Page</title>
</head>
<body>
  <div id="div_1" class="div-1">
    <button type="button">Click</button>
  </div>
</body>
</html>"#;
        let blocks = generate_dragweb_blocks(html, "[]");
        println!("Rust generated {} blocks", blocks.len());
        for b in &blocks {
            println!("b: id={}, op={}, spec={}, next={}, sub1={}, sub2={}", b.id, b.op_code, b.spec, b.next_block, b.sub_stack1, b.sub_stack2);
        }
        assert!(!blocks.is_empty());
    }
}
