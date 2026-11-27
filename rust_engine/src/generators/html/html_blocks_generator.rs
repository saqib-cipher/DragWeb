use crate::models::{parse_hex_color_i32, parse_spec_param_types, DragWebBlockBean};
use rustc_hash::FxHashMap;
use serde_json::Value;

#[derive(Clone, Debug)]
pub struct BlockDef {
    pub id: String,
    pub category: String,
    pub block_type: String,
    pub color: i32,
    pub spec: String,
    pub spec2: String,
    pub code: String,
}

#[derive(Clone, Debug)]
pub enum HtmlNode {
    Element {
        tag_name: String,
        attributes: Vec<(String, String)>,
        children: Vec<HtmlNode>,
        is_void: bool,
    },
    Text(String),
    Comment(String),
}

pub struct GeneratorContext {
    pub counter: i32,
    pub blocks: Vec<DragWebBlockBean>,
    pub tag_map: FxHashMap<String, BlockDef>,
    pub attr_map: FxHashMap<String, BlockDef>,
    pub css_prop_map: FxHashMap<String, BlockDef>,
    pub fallback_container: BlockDef,
    pub fallback_leaf: BlockDef,
    pub fallback_attr_generic: BlockDef,
    pub fallback_text: BlockDef,
    pub fallback_comment: BlockDef,
}

impl GeneratorContext {
    pub fn next_id(&mut self) -> i32 {
        let id = self.counter;
        self.counter += 1;
        id
    }

    pub fn add_block(&mut self, block: DragWebBlockBean) {
        self.blocks.push(block);
    }

    pub fn set_next_block(&mut self, from_id: i32, to_id: i32) {
        let str_id = from_id.to_string();
        if let Some(b) = self.blocks.iter_mut().find(|b| b.id == str_id) {
            b.next_block = to_id;
        }
    }
}

pub const VOID_ELEMENTS: &[&str] = &[
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
    "source", "track", "wbr",
];

pub const CONTAINER_ELEMENTS: &[&str] = &[
    "div", "section", "header", "footer", "nav", "main", "article", "aside", "form", "ul", "ol",
    "li", "dl", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "figure", "figcaption",
    "details", "summary", "dialog", "a", "select", "blockquote", "picture", "fieldset", "body",
    "head", "html", "template",
];

pub fn load_definitions(
    blocks_json: &str,
) -> (
    FxHashMap<String, BlockDef>,
    FxHashMap<String, BlockDef>,
    BlockDef,
    BlockDef,
    BlockDef,
    BlockDef,
    BlockDef,
    FxHashMap<String, BlockDef>,
) {
    let mut tag_map = FxHashMap::default();
    let mut attr_map = FxHashMap::default();
    let mut css_prop_map = FxHashMap::default();

    // Default Fallbacks
    let fallback_container = BlockDef {
        id: "view_convert".to_string(),
        category: "Views".to_string(),
        block_type: "e".to_string(),
        color: -14776091, // #1E88E5
        spec: "Convert : %m.Convert".to_string(),
        spec2: "children".to_string(),
        code: "<%s\n%s>\n%s\n</%s>".to_string(),
    };

    let fallback_leaf = BlockDef {
        id: "view_convert_leaf".to_string(),
        category: "Views".to_string(),
        block_type: "c".to_string(),
        color: -14776091, // #1E88E5
        spec: "Convert : %m.Convert".to_string(),
        spec2: String::new(),
        code: "<%s %s />".to_string(),
    };

    let fallback_attr = BlockDef {
        id: "htmlAttrGeneric".to_string(),
        category: "Identity & State".to_string(),
        block_type: " ".to_string(),
        color: -12345273, // #43A047
        spec: "%s =\" %s \"".to_string(),
        spec2: String::new(),
        code: "%1$s=\"%2$s\"".to_string(),
    };

    let fallback_text = BlockDef {
        id: "htmlText".to_string(),
        category: "Text & Typography".to_string(),
        block_type: " ".to_string(),
        color: -13022805, // #3949AB
        spec: "text: %s".to_string(),
        spec2: String::new(),
        code: "%1$s".to_string(),
    };

    let fallback_comment = BlockDef {
        id: "html_comment".to_string(),
        category: "Operators".to_string(),
        block_type: " ".to_string(),
        color: -16718218, // #00E676
        spec: "<!-- %s -->".to_string(),
        spec2: String::new(),
        code: "<!-- %1$s -->".to_string(),
    };

    // Built-in standard attribute blocks
    let default_attrs = [
        ("id", "htmlAttrId", "id=\" %s \"", "id=\"%1$s\"", -12345273),
        ("class", "htmlAttrClass", "class=\" %s \"", "class=\"%1$s\"", -12345273),
        ("src", "htmlAttrSrc", "src=\" %s \"", "src=\"%1$s\"", -12345273),
        ("href", "htmlAttrHref", "href=\" %s \"", "href=\"%1$s\"", -12345273),
        ("type", "htmlAttrType", "type=\" %m.htmlInputType \"", "type=\"%1$s\"", -12345273),
        ("placeholder", "htmlAttrPlaceholder", "placeholder=\" %s \"", "placeholder=\"%1$s\"", -12345273),
        ("title", "htmlAttrTitle", "title=\" %s \"", "title=\"%1$s\"", -12345273),
        ("alt", "htmlAttrAlt", "alt=\" %s \"", "alt=\"%1$s\"", -12345273),
        ("name", "htmlAttrName", "name=\" %s \"", "name=\"%1$s\"", -12345273),
        ("value", "htmlAttrValue", "value=\" %s \"", "value=\"%1$s\"", -12345273),
        ("target", "htmlAttrTarget", "target=\" %m.htmlTarget \"", "target=\"%1$s\"", -12345273),
        ("role", "htmlAttrRole", "role=\" %m.htmlRole \"", "role=\"%1$s\"", -12345273),
        ("aria-label", "htmlAttrAriaLabel", "aria-label=\" %s \"", "aria-label=\"%1$s\"", -12345273),
        ("for", "htmlAttrFor", "for=\" %s \"", "for=\"%1$s\"", -12345273),
        ("action", "htmlAttrAction", "action=\" %s \"", "action=\"%1$s\"", -12345273),
        ("method", "htmlAttrMethod", "method=\" %m.htmlMethod \"", "method=\"%1$s\"", -12345273),
        ("disabled", "htmlAttrDisabled", "disabled", "disabled", -12345273),
        ("checked", "htmlAttrChecked", "checked", "checked", -12345273),
        ("readonly", "htmlAttrReadonly", "readonly", "readonly", -12345273),
        ("required", "htmlAttrRequired", "required", "required", -12345273),
        ("controls", "htmlAttrControls", "controls", "controls", -12345273),
    ];

    for (k, id, spec, code, color) in default_attrs {
        attr_map.insert(
            k.to_string(),
            BlockDef {
                id: id.to_string(),
                category: "Identity & State".to_string(),
                block_type: " ".to_string(),
                color,
                spec: spec.to_string(),
                spec2: String::new(),
                code: code.to_string(),
            },
        );
    }

    // Built-in standard CSS property definitions
    let default_css_props = [
        ("width", "setWidth", "width: %s", "width: %1$s;", -1086464),
        ("height", "setHeight", "height: %s", "height: %1$s;", -1086464),
        ("min-width", "setMinWidth", "min-width: %s", "min-width: %1$s;", -1086464),
        ("max-width", "setMaxWidth", "max-width: %s", "max-width: %1$s;", -1086464),
        ("min-height", "setMinHeight", "min-height: %s", "min-height: %1$s;", -1086464),
        ("max-height", "setMaxHeight", "max-height: %s", "max-height: %1$s;", -1086464),
        ("margin", "setMargin", "margin: %s", "margin: %1$s;", -1086464),
        ("margin-top", "setMarginTop", "margin-top: %s", "margin-top: %1$s;", -1086464),
        ("margin-bottom", "setMarginBottom", "margin-bottom: %s", "margin-bottom: %1$s;", -1086464),
        ("margin-left", "setMarginLeft", "margin-left: %s", "margin-left: %1$s;", -1086464),
        ("margin-right", "setMarginRight", "margin-right: %s", "margin-right: %1$s;", -1086464),
        ("padding", "setPadding", "padding: %s", "padding: %1$s;", -1086464),
        ("padding-top", "setPaddingTop", "padding-top: %s", "padding-top: %1$s;", -1086464),
        ("padding-bottom", "setPaddingBottom", "padding-bottom: %s", "padding-bottom: %1$s;", -1086464),
        ("padding-left", "setPaddingLeft", "padding-left: %s", "padding-left: %1$s;", -1086464),
        ("padding-right", "setPaddingRight", "padding-right: %s", "padding-right: %1$s;", -1086464),
        ("display", "setDisplay", "display: %m.display", "display: %1$s;", -1086464),
        ("position", "setPosition", "position: %m.position", "position: %1$s;", -1086464),
        ("overflow", "setOverflow", "overflow: %m.overflow", "overflow: %1$s;", -1086464),
        ("gap", "setGap", "gap: %s", "gap: %1$s;", -1086464),
        ("flex-direction", "setFlexDirection", "flex-direction: %m.flexDirection", "flex-direction: %1$s;", -1086464),
        ("flex-wrap", "setFlexWrap", "flex-wrap: %m.flexWrap", "flex-wrap: %1$s;", -1086464),
        ("justify-content", "setJustifyContent", "justify-content: %m.justifyContent", "justify-content: %1$s;", -1086464),
        ("align-items", "setAlignItems", "align-items: %m.alignItems", "align-items: %1$s;", -1086464),
        ("color", "setColor", "color: %s", "color: %1$s;", -13022805),
        ("font-size", "setFontSize", "font-size: %s", "font-size: %1$s;", -13022805),
        ("font-weight", "setFontWeight", "font-weight: %m.fontWeight", "font-weight: %1$s;", -13022805),
        ("text-align", "setTextAlign", "text-align: %m.textAlign", "text-align: %1$s;", -13022805),
        ("text-decoration", "setTextDecoration", "text-decoration: %m.textDecoration", "text-decoration: %1$s;", -13022805),
        ("text-transform", "setTextTransform", "text-transform: %m.textTransform", "text-transform: %1$s;", -13022805),
        ("line-height", "setLineHeight", "line-height: %s", "line-height: %1$s;", -13022805),
        ("font-family", "setFontFamily", "font-family: %s", "font-family: %1$s;", -13022805),
        ("background-color", "setBackgroundColor", "background-color: %s", "background-color: %1$s;", -7461718),
        ("background", "setBackground", "background: %s", "background: %1$s;", -7461718),
        ("border", "setBorder", "border: %s", "border: %1$s;", -7461718),
        ("border-radius", "setRadius", "border-radius: %s", "border-radius: %1$s;", -7461718),
        ("box-shadow", "setBoxShadow", "box-shadow: %s", "box-shadow: %1$s;", -7461718),
        ("opacity", "setOpacity", "opacity: %s", "opacity: %1$s;", -7461718),
        ("cursor", "setCursor", "cursor: %m.cursor", "cursor: %1$s;", -7461718),
    ];

    for (k, id, spec, code, color) in default_css_props {
        css_prop_map.insert(
            k.to_string(),
            BlockDef {
                id: id.to_string(),
                category: if color == -1086464 {
                    "Layout & Dimensions".to_string()
                } else if color == -13022805 {
                    "Text & Typography".to_string()
                } else {
                    "Style & Appearance".to_string()
                },
                block_type: " ".to_string(),
                color,
                spec: spec.to_string(),
                spec2: String::new(),
                code: code.to_string(),
            },
        );
    }

    if let Ok(Value::Array(items)) = serde_json::from_str::<Value>(blocks_json) {
        for item in items {
            let id = item.get("id").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let cat = item.get("category").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let raw_btype = item.get("blockType").or_else(|| item.get("type")).and_then(|v| v.as_str()).unwrap_or(" ");
            let btype = if raw_btype.is_empty() || raw_btype.eq_ignore_ascii_case("normal") {
                " ".to_string()
            } else {
                raw_btype.to_string()
            };
            let color_str = item.get("color").and_then(|v| v.as_str()).unwrap_or("#607D8B");
            let color = parse_hex_color_i32(color_str);
            let spec = item.get("spec").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let spec2 = item.get("spec2").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let code = item.get("code").and_then(|v| v.as_str()).unwrap_or("").to_string();

            let def = BlockDef {
                id: id.clone(),
                category: cat.clone(),
                block_type: btype,
                color,
                spec: spec.clone(),
                spec2,
                code: code.clone(),
            };

            if cat.eq_ignore_ascii_case("html") || id.starts_with("htmlTag") {
                let tag_key = id
                    .trim_start_matches("htmlTag")
                    .trim_start_matches("html_tag_")
                    .to_lowercase();
                if !tag_key.is_empty() {
                    tag_map.insert(tag_key, def.clone());
                }
            } else if cat.eq_ignore_ascii_case("html_attr") || id.starts_with("htmlAttr") {
                let attr_key = id
                    .trim_start_matches("htmlAttr")
                    .trim_start_matches("html_attr_")
                    .to_lowercase();
                if !attr_key.is_empty() {
                    attr_map.insert(attr_key, def.clone());
                }
            }

            if (cat.starts_with("css") || id.starts_with("css_") || id.starts_with("set"))
                && !id.starts_with("cssSelector")
                && !id.starts_with("cssHover")
                && !id.starts_with("cssFocus")
                && !id.starts_with("cssMediaQuery")
            {
                if let Some(colon_pos) = code.find(':') {
                    let code_key = code[..colon_pos].trim().to_lowercase();
                    if !code_key.is_empty() && !code_key.contains('%') && !code_key.contains(' ') {
                        css_prop_map.insert(code_key, def.clone());
                    }
                }
                let raw_name = id.trim_start_matches("css_").trim_start_matches("set");
                let mut kebab = String::new();
                for (ci, ch) in raw_name.chars().enumerate() {
                    if ch.is_uppercase() && ci > 0 {
                        kebab.push('-');
                    }
                    kebab.push(ch.to_ascii_lowercase());
                }
                if !kebab.is_empty() {
                    css_prop_map.entry(kebab).or_insert(def);
                }
            }
        }
    }

    (
        tag_map,
        attr_map,
        fallback_container,
        fallback_leaf,
        fallback_attr,
        fallback_text,
        fallback_comment,
        css_prop_map,
    )
}

fn parse_html_custom(html: &str) -> Vec<HtmlNode> {
    let mut nodes = Vec::new();
    let chars: Vec<char> = html.chars().collect();
    let len = chars.len();
    let mut i = 0;

    while i < len {
        if chars[i] == '<' {
            if i + 3 < len && chars[i + 1] == '!' && chars[i + 2] == '-' && chars[i + 3] == '-' {
                let start = i + 4;
                let mut end = start;
                while end + 2 < len && !(chars[end] == '-' && chars[end + 1] == '-' && chars[end + 2] == '>') {
                    end += 1;
                }
                let comment_text: String = chars[start..end].iter().collect();
                nodes.push(HtmlNode::Comment(comment_text));
                i = if end + 2 < len { end + 3 } else { len };
                continue;
            }

            if i + 8 < len {
                let sub: String = chars[i + 1..i + 9].iter().collect();
                if sub.eq_ignore_ascii_case("!doctype") {
                    while i < len && chars[i] != '>' {
                        i += 1;
                    }
                    if i < len {
                        i += 1;
                    }
                    continue;
                }
            }

            if i + 1 < len && chars[i + 1] == '/' {
                while i < len && chars[i] != '>' {
                    i += 1;
                }
                if i < len {
                    i += 1;
                }
                continue;
            }

            i += 1;
            while i < len && chars[i].is_whitespace() {
                i += 1;
            }
            let tag_start = i;
            while i < len && !chars[i].is_whitespace() && chars[i] != '>' && chars[i] != '/' {
                i += 1;
            }
            let tag_name: String = chars[tag_start..i].iter().collect();
            let tag_lower = tag_name.to_ascii_lowercase();

            let mut attrs = Vec::new();
            let mut is_self_closing = false;

            while i < len && chars[i] != '>' {
                if chars[i] == '/' && i + 1 < len && chars[i + 1] == '>' {
                    is_self_closing = true;
                    i += 1;
                    break;
                }
                if chars[i].is_whitespace() {
                    i += 1;
                    continue;
                }

                let attr_start = i;
                while i < len && !chars[i].is_whitespace() && chars[i] != '=' && chars[i] != '>' && chars[i] != '/' {
                    i += 1;
                }
                let attr_name: String = chars[attr_start..i].iter().collect();

                while i < len && chars[i].is_whitespace() {
                    i += 1;
                }

                let mut attr_val = String::new();
                if i < len && chars[i] == '=' {
                    i += 1;
                    while i < len && chars[i].is_whitespace() {
                        i += 1;
                    }
                    if i < len && (chars[i] == '"' || chars[i] == '\'') {
                        let quote = chars[i];
                        i += 1;
                        let val_start = i;
                        while i < len && chars[i] != quote {
                            i += 1;
                        }
                        attr_val = chars[val_start..i].iter().collect();
                        if i < len {
                            i += 1;
                        }
                    } else {
                        let val_start = i;
                        while i < len && !chars[i].is_whitespace() && chars[i] != '>' && chars[i] != '/' {
                            i += 1;
                        }
                        attr_val = chars[val_start..i].iter().collect();
                    }
                }

                if !attr_name.is_empty() {
                    attrs.push((attr_name, attr_val));
                }
            }

            if i < len && chars[i] == '>' {
                i += 1;
            }

            let is_void = is_self_closing || VOID_ELEMENTS.contains(&tag_lower.as_str());

            if is_void {
                nodes.push(HtmlNode::Element {
                    tag_name,
                    attributes: attrs,
                    children: Vec::new(),
                    is_void: true,
                });
            } else if tag_lower == "script" || tag_lower == "style" {
                let close_tag = format!("</{}>", tag_lower);
                let content_start = i;
                let mut content_end = content_start;
                while content_end + close_tag.len() <= len {
                    let sub: String = chars[content_end..content_end + close_tag.len()].iter().collect();
                    if sub.eq_ignore_ascii_case(&close_tag) {
                        break;
                    }
                    content_end += 1;
                }
                if content_end + close_tag.len() > len {
                    content_end = len;
                }
                let inner_raw: String = chars[content_start..content_end].iter().collect();
                let children = if inner_raw.trim().is_empty() {
                    Vec::new()
                } else {
                    vec![HtmlNode::Text(inner_raw)]
                };
                nodes.push(HtmlNode::Element {
                    tag_name,
                    attributes: attrs,
                    children,
                    is_void: false,
                });
                i = content_end;
                while i < len && chars[i] != '>' {
                    i += 1;
                }
                if i < len {
                    i += 1;
                }
            } else {
                let close_tag = format!("</{}>", tag_lower);
                let content_start = i;
                let mut content_end = content_start;
                let mut nested = 1;

                while content_end < len && nested > 0 {
                    if chars[content_end] == '<' {
                        if content_end + 1 < len && chars[content_end + 1] == '/' {
                            let end_close = content_end + close_tag.len();
                            if end_close <= len {
                                let sub: String = chars[content_end..end_close].iter().collect();
                                if sub.eq_ignore_ascii_case(&close_tag) {
                                    nested -= 1;
                                    if nested == 0 {
                                        break;
                                    }
                                }
                            }
                        } else {
                            let open_prefix = format!("<{}", tag_lower);
                            let end_open = content_end + open_prefix.len();
                            if end_open <= len {
                                let sub: String = chars[content_end..end_open].iter().collect();
                                if sub.eq_ignore_ascii_case(&open_prefix) {
                                    if end_open < len && (chars[end_open].is_whitespace() || chars[end_open] == '>' || chars[end_open] == '/') {
                                        nested += 1;
                                    }
                                }
                            }
                        }
                    }
                    content_end += 1;
                }

                let inner_html: String = chars[content_start..content_end].iter().collect();
                let children = parse_html_custom(&inner_html);

                nodes.push(HtmlNode::Element {
                    tag_name,
                    attributes: attrs,
                    children,
                    is_void: false,
                });

                i = content_end;
                while i < len && chars[i] != '>' {
                    i += 1;
                }
                if i < len {
                    i += 1;
                }
            }
        } else {
            let text_start = i;
            while i < len && chars[i] != '<' {
                i += 1;
            }
            let text: String = chars[text_start..i].iter().collect();
            if !text.trim().is_empty() {
                nodes.push(HtmlNode::Text(text));
            }
        }
    }

    nodes
}

pub fn unpack_style_properties(style_str: &str, ctx: &mut GeneratorContext) -> Vec<DragWebBlockBean> {
    let mut prop_blocks = Vec::new();
    let declarations: Vec<&str> = style_str.split(';').map(|s| s.trim()).filter(|s| !s.is_empty()).collect();

    for decl in declarations {
        if let Some(colon_pos) = decl.find(':') {
            let key = decl[..colon_pos].trim().to_lowercase();
            let val = decl[colon_pos + 1..].trim();

            let prop_block_id = ctx.next_id();
            let mut prop_block = DragWebBlockBean::default();
            prop_block.id = prop_block_id.to_string();
            prop_block.next_block = -1;
            prop_block.sub_stack1 = -1;
            prop_block.sub_stack2 = -1;
            prop_block.is_connected_to_root = false;

            if let Some(def) = ctx.css_prop_map.get(&key) {
                prop_block.op_code = def.id.clone();
                prop_block.spec = def.spec.clone();
                prop_block.code = def.code.clone();
                prop_block.category = def.category.clone();
                prop_block.block_type = def.block_type.clone();
                prop_block.color = def.color;
                prop_block.parameters = vec![val.to_string()];
                prop_block.param_types = parse_spec_param_types(&def.spec);
                prop_block.tokenized_spec = prop_block.param_types.clone();
            } else {
                let color = if key.contains("width") || key.contains("height") || key.contains("margin") || key.contains("padding") || key.contains("display") || key.contains("position") {
                    -1086464 // #EF6C00
                } else if key.contains("color") || key.contains("font") || key.contains("text") {
                    -13022805 // #3949AB
                } else {
                    -7461718 // #8E24AA
                };

                let op_code = match key.as_str() {
                    "width" => "setWidth",
                    "height" => "setHeight",
                    "margin" => "setMargin",
                    "padding" => "setPadding",
                    "color" => "setColor",
                    "background-color" => "setBackgroundColor",
                    "background" => "setBackground",
                    "font-size" => "setFontSize",
                    "border" => "setBorder",
                    "border-radius" => "setRadius",
                    _ => "cssPropertyGeneric",
                };

                prop_block.op_code = op_code.to_string();
                prop_block.spec = format!("{}: %s", key);
                prop_block.code = format!("{}: %1$s;", key);
                prop_block.category = "Style & Appearance".to_string();
                prop_block.block_type = " ".to_string();
                prop_block.color = color;
                prop_block.parameters = vec![val.to_string()];
                prop_block.param_types = vec!["s".to_string()];
                prop_block.tokenized_spec = vec!["s".to_string()];
            }

            prop_blocks.push(prop_block);
        }
    }

    prop_blocks
}

pub fn process_node(ctx: &mut GeneratorContext, node: &HtmlNode, is_root: bool) -> i32 {
    match node {
        HtmlNode::Comment(comment) => {
            let id = ctx.next_id();
            let trimmed_comment = comment.trim().to_string();
            let b = DragWebBlockBean {
                id: id.to_string(),
                op_code: ctx.fallback_comment.id.clone(),
                spec: ctx.fallback_comment.spec.clone(),
                spec2: String::new(),
                block_type: ctx.fallback_comment.block_type.clone(),
                category: ctx.fallback_comment.category.clone(),
                color: ctx.fallback_comment.color,
                code: ctx.fallback_comment.code.clone(),
                parameters: vec![trimmed_comment],
                param_types: vec!["s".to_string()],
                sub_stack1: -1,
                sub_stack2: -1,
                next_block: -1,
                stack_index: 0,
                is_connected_to_root: is_root,
                x: 0,
                y: 0,
                header_text: String::new(),
                tokenized_spec: vec!["s".to_string()],
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
                header_text: String::new(),
                tokenized_spec: vec!["s".to_string()],
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
            let tag_lower = tag_name.to_ascii_lowercase();

            let has_child_elements = children.iter().any(|c| matches!(c, HtmlNode::Element { .. }));
            let text_content = children
                .iter()
                .filter_map(|c| match c {
                    HtmlNode::Text(t) => Some(t.trim()),
                    _ => None,
                })
                .collect::<Vec<_>>()
                .join(" ");

            let is_container_tag = CONTAINER_ELEMENTS.contains(&tag_lower.as_str());
            let is_leaf = *is_void || (!is_container_tag && !has_child_elements);

            let mut view_def = if is_leaf {
                ctx.fallback_leaf.clone()
            } else {
                ctx.fallback_container.clone()
            };

            if let Some(def) = ctx.tag_map.get(&tag_lower) {
                view_def.id = def.id.clone();
                view_def.color = def.color;
            }

            let view_id = ctx.next_id();

            let mut first_attr_id = -1;
            let mut prev_attr_id = -1;

            for (attr_name, attr_val) in attributes {
                let attr_lower = attr_name.to_ascii_lowercase();

                if attr_lower == "style" {
                    let style_blocks = unpack_style_properties(attr_val, ctx);
                    for sb in style_blocks {
                        let sid: i32 = sb.id.parse().unwrap_or(-1);
                        if first_attr_id == -1 {
                            first_attr_id = sid;
                        }
                        if prev_attr_id != -1 {
                            ctx.set_next_block(prev_attr_id, sid);
                        }
                        prev_attr_id = sid;
                        ctx.add_block(sb);
                    }
                    continue;
                }

                let attr_id = ctx.next_id();
                let mut attr_block = DragWebBlockBean::default();
                attr_block.id = attr_id.to_string();
                attr_block.next_block = -1;
                attr_block.sub_stack1 = -1;
                attr_block.sub_stack2 = -1;
                attr_block.is_connected_to_root = false;

                if let Some(def) = ctx.attr_map.get(&attr_lower) {
                    attr_block.op_code = def.id.clone();
                    attr_block.spec = def.spec.clone();
                    attr_block.code = def.code.clone();
                    attr_block.category = def.category.clone();
                    attr_block.block_type = def.block_type.clone();
                    attr_block.color = def.color;
                    let param_types = parse_spec_param_types(&def.spec);
                    attr_block.parameters = if param_types.is_empty() {
                        Vec::new()
                    } else {
                        vec![attr_val.clone()]
                    };
                    attr_block.param_types = param_types;
                    attr_block.tokenized_spec = attr_block.param_types.clone();
                } else if attr_lower.starts_with("data-") && attr_name.len() > 5 {
                    attr_block.op_code = "htmlAttrData".to_string();
                    attr_block.spec = "data- %s =\" %s \"".to_string();
                    attr_block.code = "data-%1$s=\"%2$s\"".to_string();
                    attr_block.category = "Identity & State".to_string();
                    attr_block.block_type = " ".to_string();
                    attr_block.color = -12345273;
                    attr_block.parameters = vec![attr_name[5..].to_string(), attr_val.clone()];
                    attr_block.param_types = vec!["s".to_string(), "s".to_string()];
                    attr_block.tokenized_spec = attr_block.param_types.clone();
                } else {
                    attr_block.op_code = ctx.fallback_attr_generic.id.clone();
                    attr_block.spec = ctx.fallback_attr_generic.spec.clone();
                    attr_block.code = ctx.fallback_attr_generic.code.clone();
                    attr_block.category = ctx.fallback_attr_generic.category.clone();
                    attr_block.block_type = ctx.fallback_attr_generic.block_type.clone();
                    attr_block.color = ctx.fallback_attr_generic.color;
                    attr_block.parameters = vec![attr_name.clone(), attr_val.clone()];
                    attr_block.param_types = vec!["s".to_string(), "s".to_string()];
                    attr_block.tokenized_spec = vec!["s".to_string(), "s".to_string()];
                }

                if first_attr_id == -1 {
                    first_attr_id = attr_id;
                }
                if prev_attr_id != -1 {
                    ctx.set_next_block(prev_attr_id, attr_id);
                }
                prev_attr_id = attr_id;
                ctx.add_block(attr_block);
            }

            if !text_content.trim().is_empty() && !has_child_elements {
                let text_id = ctx.next_id();
                let text_block = DragWebBlockBean {
                    id: text_id.to_string(),
                    op_code: "htmlText".to_string(),
                    spec: "text: %s".to_string(),
                    spec2: String::new(),
                    block_type: " ".to_string(),
                    category: "Text & Typography".to_string(),
                    color: -13022805, // #3949AB
                    code: "%1$s".to_string(),
                    parameters: vec![text_content.trim().to_string()],
                    param_types: vec!["s".to_string()],
                    sub_stack1: -1,
                    sub_stack2: -1,
                    next_block: -1,
                    stack_index: -1,
                    is_connected_to_root: false,
                    x: 0,
                    y: 0,
                    header_text: String::new(),
                    tokenized_spec: vec!["s".to_string()],
                };
                if first_attr_id == -1 {
                    first_attr_id = text_id;
                }
                if prev_attr_id != -1 {
                    ctx.set_next_block(prev_attr_id, text_id);
                }
                ctx.add_block(text_block);
            }

            let mut first_child_id = -1;
            let mut prev_child_id = -1;

            if !is_leaf {
                for child in children {
                    let child_id = process_node(ctx, child, false);
                    if child_id != -1 {
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
                id: view_id.to_string(),
                op_code: view_def.id,
                spec: view_def.spec,
                spec2: view_def.spec2,
                block_type: view_def.block_type,
                category: "Views".to_string(),
                color: -14776091, // #1E88E5
                code: view_def.code,
                parameters: vec![tag_lower],
                param_types: vec!["m.Convert".to_string()],
                sub_stack1: first_attr_id,
                sub_stack2: first_child_id,
                next_block: -1,
                stack_index: if is_root { 0 } else { -1 },
                is_connected_to_root: is_root,
                x: if is_root { 100 } else { 0 },
                y: if is_root { 100 } else { 0 },
                header_text: String::new(),
                tokenized_spec: vec!["m.Convert".to_string()],
            };

            ctx.add_block(view_block);
            view_id
        }
    }
}

pub fn generate_dragweb_blocks(html_code: &str, blocks_json: &str) -> Vec<DragWebBlockBean> {
    let (tag_map, attr_map, fb_cont, fb_leaf, fb_attr, fb_text, fb_comm, css_prop_map) =
        load_definitions(blocks_json);

    let mut ctx = GeneratorContext {
        counter: 10,
        blocks: Vec::new(),
        tag_map,
        attr_map,
        css_prop_map,
        fallback_container: fb_cont,
        fallback_leaf: fb_leaf,
        fallback_attr_generic: fb_attr,
        fallback_text: fb_text,
        fallback_comment: fb_comm,
    };

    let nodes = parse_html_custom(html_code);

    let body_nodes = if let Some(HtmlNode::Element { children, .. }) = nodes.iter().find(|n| {
        if let HtmlNode::Element { tag_name, .. } = n {
            tag_name.eq_ignore_ascii_case("html")
        } else {
            false
        }
    }) {
        if let Some(HtmlNode::Element { children: b_children, .. }) = children.iter().find(|n| {
            if let HtmlNode::Element { tag_name, .. } = n {
                tag_name.eq_ignore_ascii_case("body")
            } else {
                false
            }
        }) {
            b_children.clone()
        } else {
            nodes
        }
    } else {
        nodes
    };

    let mut prev_root_id = -1;
    for node in body_nodes.iter() {
        let is_root = prev_root_id == -1;
        let id = process_node(&mut ctx, node, is_root);
        if id != -1 {
            if prev_root_id != -1 {
                ctx.set_next_block(prev_root_id, id);
            }
            prev_root_id = id;
        }
    }

    ctx.blocks
}

fn escape_html_attr(val: &str) -> String {
    val.replace('"', "&quot;")
}

fn format_block_template(template: &str, params: &[String]) -> String {
    let mut out = template.to_string();
    for (idx, p) in params.iter().enumerate() {
        let numbered = format!("%{}$s", idx + 1);
        if out.contains(&numbered) {
            out = out.replace(&numbered, p);
        }
    }
    for p in params {
        if let Some(pos) = out.find("%s") {
            out.replace_range(pos..pos + 2, p);
        }
    }
    out
}

fn css_prop_from_block(block: &DragWebBlockBean) -> Option<String> {
    let op = block.op_code.as_str();
    let p0 = block.parameters.first().map(|s| s.trim()).unwrap_or("");
    let p1 = block.parameters.get(1).map(|s| s.trim()).unwrap_or("");

    let prop_name = match op {
        "setWidth" => Some("width"),
        "setHeight" => Some("height"),
        "setMinWidth" => Some("min-width"),
        "setMaxWidth" => Some("max-width"),
        "setMinHeight" => Some("min-height"),
        "setMaxHeight" => Some("max-height"),
        "setMargin" => Some("margin"),
        "setMarginTop" => Some("margin-top"),
        "setMarginBottom" => Some("margin-bottom"),
        "setMarginLeft" => Some("margin-left"),
        "setMarginRight" => Some("margin-right"),
        "setPadding" => Some("padding"),
        "setPaddingTop" => Some("padding-top"),
        "setPaddingBottom" => Some("padding-bottom"),
        "setPaddingLeft" => Some("padding-left"),
        "setPaddingRight" => Some("padding-right"),
        "setDisplay" => Some("display"),
        "setPosition" => Some("position"),
        "setOverflow" => Some("overflow"),
        "setGap" => Some("gap"),
        "setFlexDirection" => Some("flex-direction"),
        "setFlexWrap" => Some("flex-wrap"),
        "setJustifyContent" => Some("justify-content"),
        "setAlignItems" => Some("align-items"),
        "setColor" => Some("color"),
        "setFontSize" => Some("font-size"),
        "setFontWeight" => Some("font-weight"),
        "setTextAlign" => Some("text-align"),
        "setTextDecoration" => Some("text-decoration"),
        "setTextTransform" => Some("text-transform"),
        "setLineHeight" => Some("line-height"),
        "setFontFamily" => Some("font-family"),
        "setBackgroundColor" => Some("background-color"),
        "setBackground" => Some("background"),
        "setBorder" => Some("border"),
        "setRadius" => Some("border-radius"),
        "setBoxShadow" => Some("box-shadow"),
        "setOpacity" => Some("opacity"),
        "setCursor" => Some("cursor"),
        _ => None,
    };

    if let Some(name) = prop_name {
        if p0.is_empty() {
            return None;
        }
        return Some(format!("{}: {};", name, p0));
    }

    if op == "cssPropertyGeneric" || op == "setStyleProp" {
        if block.parameters.len() >= 2 {
            if p0.is_empty() || p1.is_empty() {
                return None;
            }
            return Some(format!("{}: {};", p0, p1));
        }
        if let Some(colon) = block.spec.find(':') {
            let k = block.spec[..colon].trim();
            if !k.is_empty() && !k.contains('%') && !p0.is_empty() {
                return Some(format!("{}: {};", k, p0));
            }
        }
    }

    if (op.starts_with("set") || op.starts_with("css_")) && !block.code.is_empty() && block.code.contains(':') {
        let rendered = format_block_template(&block.code, &block.parameters);
        let trimmed = rendered.trim();
        if !trimmed.is_empty() {
            return Some(if trimmed.ends_with(';') {
                trimmed.to_string()
            } else {
                format!("{};", trimmed)
            });
        }
    }

    None
}

fn attr_from_block(block: &DragWebBlockBean) -> Option<String> {
    let op = block.op_code.as_str();
    let p0 = block.parameters.first().map(|s| s.as_str()).unwrap_or("");
    let p1 = block.parameters.get(1).map(|s| s.as_str()).unwrap_or("");

    let boolean_attr = match op {
        "htmlAttrDisabled" => Some("disabled"),
        "htmlAttrChecked" => Some("checked"),
        "htmlAttrReadonly" => Some("readonly"),
        "htmlAttrRequired" => Some("required"),
        "htmlAttrControls" => Some("controls"),
        "htmlAttrSelected" => Some("selected"),
        "htmlAttrMultiple" => Some("multiple"),
        "htmlAttrHidden" => Some("hidden"),
        _ => None,
    };
    if let Some(bname) = boolean_attr {
        return Some(bname.to_string());
    }

    let named_attr = match op {
        "htmlAttrId" => Some("id"),
        "htmlAttrClass" => Some("class"),
        "htmlAttrName" => Some("name"),
        "htmlAttrValue" => Some("value"),
        "htmlAttrType" => Some("type"),
        "htmlAttrTitle" => Some("title"),
        "htmlAttrRole" => Some("role"),
        "htmlAttrAriaLabel" => Some("aria-label"),
        "htmlAttrSrc" => Some("src"),
        "htmlAttrAlt" => Some("alt"),
        "htmlAttrHref" => Some("href"),
        "htmlAttrTarget" => Some("target"),
        "htmlAttrPlaceholder" => Some("placeholder"),
        "htmlAttrFor" => Some("for"),
        "htmlAttrAction" => Some("action"),
        "htmlAttrMethod" => Some("method"),
        _ => None,
    };
    if let Some(aname) = named_attr {
        return Some(format!("{}=\"{}\"", aname, escape_html_attr(p0)));
    }

    if op == "htmlAttrData" {
        let key = p0.trim();
        if key.is_empty() {
            return None;
        }
        return Some(format!("data-{}=\"{}\"", key, escape_html_attr(p1)));
    }

    if op == "htmlAttrGeneric" {
        let key = p0.trim();
        if key.is_empty() {
            return None;
        }
        return Some(format!("{}=\"{}\"", key, escape_html_attr(p1)));
    }

    if op.starts_with("htmlAttr") && !block.code.is_empty() {
        let rendered = format_block_template(&block.code, &block.parameters);
        let trimmed = rendered.trim();
        if !trimmed.is_empty() {
            return Some(trimmed.to_string());
        }
    }

    None
}

fn render_block_chain(
    start_id: i32,
    block_map: &FxHashMap<i32, &DragWebBlockBean>,
    visited: &mut std::collections::HashSet<i32>,
    indent: usize,
) -> String {
    let mut out = String::new();
    let mut curr_id = start_id;
    let indent_str = "  ".repeat(indent);

    while curr_id >= 0 {
        if !visited.insert(curr_id) {
            break;
        }
        let Some(block) = block_map.get(&curr_id) else {
            break;
        };

        let op = block.op_code.as_str();
        if op == "view_convert" || op == "view_convert_leaf" || op.starts_with("htmlTag") {
            let tag = if op == "view_convert" || op == "view_convert_leaf" {
                block
                    .parameters
                    .first()
                    .map(|s| s.trim())
                    .filter(|s| !s.is_empty())
                    .unwrap_or(if op == "view_convert" { "div" } else { "p" })
                    .to_ascii_lowercase()
            } else {
                op.trim_start_matches("htmlTag").to_ascii_lowercase()
            };

            let mut attrs: Vec<String> = Vec::new();
            let mut styles: Vec<String> = Vec::new();
            let mut inner_texts: Vec<String> = Vec::new();
            let mut extra_inner: Vec<String> = Vec::new();

            let mut attr_id = block.sub_stack1;
            while attr_id >= 0 {
                if !visited.insert(attr_id) {
                    break;
                }
                let Some(ab) = block_map.get(&attr_id) else {
                    break;
                };
                if ab.op_code == "htmlText" {
                    if let Some(txt) = ab.parameters.first() {
                        if !txt.is_empty() {
                            inner_texts.push(txt.clone());
                        }
                    }
                } else if ab.op_code == "html_direct_code" {
                    if let Some(raw) = ab.parameters.first() {
                        if !raw.trim().is_empty() {
                            extra_inner.push(raw.clone());
                        }
                    }
                } else if ab.op_code == "html_comment" {
                    let c = ab.parameters.first().map(|s| s.trim()).unwrap_or("");
                    extra_inner.push(format!("<!-- {} -->", c));
                } else if let Some(decl) = css_prop_from_block(ab) {
                    styles.push(decl);
                } else if let Some(attr) = attr_from_block(ab) {
                    attrs.push(attr);
                }
                attr_id = ab.next_block;
            }

            if !styles.is_empty() {
                attrs.push(format!("style=\"{}\"", escape_html_attr(&styles.join(" "))));
            }

            let attr_str = if attrs.is_empty() {
                String::new()
            } else {
                format!(" {}", attrs.join(" "))
            };

            if !out.is_empty() {
                out.push('\n');
            }

            let is_void = VOID_ELEMENTS.contains(&tag.as_str());
            if is_void {
                out.push_str(&format!("{}<{}{} />", indent_str, tag, attr_str));
            } else {
                let children_html = if block.sub_stack2 >= 0 {
                    render_block_chain(block.sub_stack2, block_map, visited, indent + 1)
                } else {
                    String::new()
                };

                let mut inline_parts = inner_texts;
                inline_parts.extend(extra_inner);
                let inline_content = inline_parts.join(" ");

                if !children_html.is_empty() {
                    out.push_str(&format!("{}<{}{}>\n", indent_str, tag, attr_str));
                    if !inline_content.is_empty() {
                        out.push_str(&format!("{}  {}\n", indent_str, inline_content));
                    }
                    out.push_str(&children_html);
                    out.push_str(&format!("\n{}</{}>", indent_str, tag));
                } else {
                    out.push_str(&format!(
                        "{}<{}{}>{}</{}>",
                        indent_str, tag, attr_str, inline_content, tag
                    ));
                }
            }
        } else if op == "htmlText" {
            let t = block.parameters.first().map(|s| s.as_str()).unwrap_or("");
            if !t.is_empty() {
                if !out.is_empty() {
                    out.push('\n');
                }
                out.push_str(&format!("{}{}", indent_str, t));
            }
        } else if op == "html_comment" {
            let c = block.parameters.first().map(|s| s.trim()).unwrap_or("");
            if !out.is_empty() {
                out.push('\n');
            }
            out.push_str(&format!("{}<!-- {} -->", indent_str, c));
        } else if op == "html_doctype" {
            if !out.is_empty() {
                out.push('\n');
            }
            out.push_str(&format!("{}<!DOCTYPE html>", indent_str));
        } else if op == "html_direct_code" {
            let raw = block.parameters.first().map(|s| s.as_str()).unwrap_or("");
            if !raw.trim().is_empty() {
                if !out.is_empty() {
                    out.push('\n');
                }
                for line in raw.lines() {
                    out.push_str(&format!("{}{}\n", indent_str, line));
                }
                if out.ends_with('\n') {
                    out.pop();
                }
            }
        } else if !block.code.is_empty() {
            let rendered = format_block_template(&block.code, &block.parameters);
            if !rendered.trim().is_empty() {
                if !out.is_empty() {
                    out.push('\n');
                }
                out.push_str(&format!("{}{}", indent_str, rendered.trim()));
            }
        }

        curr_id = block.next_block;
    }

    out
}

pub fn generate_html_from_blocks(blocks_json: &str) -> String {
    let blocks: Vec<DragWebBlockBean> = match serde_json::from_str(blocks_json) {
        Ok(b) => b,
        Err(_) => return String::new(),
    };
    if blocks.is_empty() {
        return String::new();
    }

    let mut block_map: FxHashMap<i32, &DragWebBlockBean> = FxHashMap::default();
    let mut referenced: std::collections::HashSet<i32> = std::collections::HashSet::new();

    for b in &blocks {
        if let Ok(id) = b.id.parse::<i32>() {
            block_map.insert(id, b);
        }
        if b.next_block >= 0 {
            referenced.insert(b.next_block);
        }
        if b.sub_stack1 >= 0 {
            referenced.insert(b.sub_stack1);
        }
        if b.sub_stack2 >= 0 {
            referenced.insert(b.sub_stack2);
        }
    }

    let mut root_ids: Vec<i32> = blocks
        .iter()
        .filter(|b| b.is_connected_to_root)
        .filter_map(|b| b.id.parse::<i32>().ok())
        .collect();

    if root_ids.is_empty() {
        let mut heads: Vec<&DragWebBlockBean> = blocks
            .iter()
            .filter(|b| b.id.parse::<i32>().map(|id| !referenced.contains(&id)).unwrap_or(false))
            .collect();
        heads.sort_by_key(|b| (b.y, b.id.parse::<i32>().unwrap_or(i32::MAX)));
        root_ids = heads
            .into_iter()
            .filter_map(|b| b.id.parse::<i32>().ok())
            .collect();
    }

    let mut visited = std::collections::HashSet::new();
    let mut out_parts = Vec::new();
    for root_id in root_ids {
        if !visited.contains(&root_id) {
            let s = render_block_chain(root_id, &block_map, &mut visited, 0);
            if !s.trim().is_empty() {
                out_parts.push(s);
            }
        }
    }

    out_parts.join("\n")
}
