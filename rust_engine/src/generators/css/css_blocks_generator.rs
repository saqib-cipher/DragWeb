use crate::generators::html::html_blocks_generator::{
    load_definitions, unpack_style_properties, BlockDef, GeneratorContext,
};
use crate::models::{parse_hex_color_i32, parse_spec_param_types, DragWebBlockBean};
use rustc_hash::FxHashMap;
use serde_json::Value;

#[derive(Clone, Debug)]
pub struct CssRule {
    pub selector: String,
    pub declarations: Vec<(String, String)>,
    pub is_media: bool,
    pub is_comment: bool,
    pub media_query: String,
    pub inner_rules: Vec<CssRule>,
}

fn load_css_structural_defs(blocks_json: &str) -> FxHashMap<String, BlockDef> {
    let mut map = FxHashMap::default();
    if let Ok(Value::Array(items)) = serde_json::from_str::<Value>(blocks_json) {
        for item in items {
            let id = item.get("id").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let cat = item.get("category").and_then(|v| v.as_str()).unwrap_or("").to_string();
            if id.is_empty() {
                continue;
            }
            if id == "cssSelector"
                || id == "cssSelectorS"
                || id == "cssHover"
                || id == "cssFocus"
                || id == "cssActive"
                || id == "cssMediaQuery"
                || id == "commentBlockCss"
                || id == "asdCss"
            {
                let raw_btype = item
                    .get("blockType")
                    .or_else(|| item.get("type"))
                    .and_then(|v| v.as_str())
                    .unwrap_or(" ");
                let block_type = if raw_btype.is_empty() || raw_btype.eq_ignore_ascii_case("normal") {
                    " ".to_string()
                } else {
                    raw_btype.to_string()
                };
                let color_str = item.get("color").and_then(|v| v.as_str()).unwrap_or("#1976D2");
                let def = BlockDef {
                    id: id.clone(),
                    category: cat,
                    block_type,
                    color: parse_hex_color_i32(color_str),
                    spec: item.get("spec").and_then(|v| v.as_str()).unwrap_or("").to_string(),
                    spec2: item.get("spec2").and_then(|v| v.as_str()).unwrap_or("").to_string(),
                    code: item.get("code").and_then(|v| v.as_str()).unwrap_or("").to_string(),
                };
                map.insert(id, def);
            }
        }
    }
    map
}

pub fn parse_css_rules(css: &str) -> Vec<CssRule> {
    let mut rules = Vec::new();
    let chars: Vec<char> = css.chars().collect();
    let len = chars.len();
    let mut i = 0;

    while i < len {
        while i < len && chars[i].is_whitespace() {
            i += 1;
        }
        if i >= len {
            break;
        }

        // Preserve CSS comments as comment blocks
        if chars[i] == '/' && i + 1 < len && chars[i + 1] == '*' {
            let comment_start = i + 2;
            let mut comment_end = comment_start;
            while comment_end + 1 < len && !(chars[comment_end] == '*' && chars[comment_end + 1] == '/') {
                comment_end += 1;
            }
            let comment_text: String = chars[comment_start..comment_end].iter().collect();
            let trimmed = comment_text.trim().to_string();
            if !trimmed.is_empty() {
                rules.push(CssRule {
                    selector: String::new(),
                    declarations: Vec::new(),
                    is_media: false,
                    is_comment: true,
                    media_query: trimmed,
                    inner_rules: Vec::new(),
                });
            }
            i = if comment_end + 1 < len { comment_end + 2 } else { len };
            continue;
        }

        // Skip at-directives terminated by semicolon before a block (e.g. @import url("theme.css");)
        if chars[i] == '@' {
            let mut look = i;
            let mut is_semicolon_directive = false;
            while look < len {
                if chars[look] == ';' {
                    is_semicolon_directive = true;
                    i = look + 1;
                    break;
                }
                if chars[look] == '{' {
                    break;
                }
                look += 1;
            }
            if is_semicolon_directive {
                continue;
            }
        }

        // Read selector / at-rule
        let sel_start = i;
        while i < len && chars[i] != '{' {
            i += 1;
        }
        if i >= len {
            break;
        }
        let selector: String = chars[sel_start..i].iter().collect();
        let selector = selector.trim().to_string();
        i += 1; // skip {

        if selector.starts_with("@media") {
            let media_query = selector.trim_start_matches("@media").trim().to_string();
            let mut brace_depth = 1;
            let inner_start = i;
            while i < len && brace_depth > 0 {
                if chars[i] == '{' {
                    brace_depth += 1;
                } else if chars[i] == '}' {
                    brace_depth -= 1;
                }
                i += 1;
            }
            let end_slice = if brace_depth == 0 && i > 0 { i - 1 } else { len };
            let inner_css: String = chars[inner_start..end_slice].iter().collect();
            let inner_rules = parse_css_rules(&inner_css);
            rules.push(CssRule {
                selector,
                declarations: Vec::new(),
                is_media: true,
                is_comment: false,
                media_query,
                inner_rules,
            });
            continue;
        }

        let mut decls = Vec::new();
        let mut decl_start = i;
        while i < len && chars[i] != '}' {
            if chars[i] == ';' {
                let decl_str: String = chars[decl_start..i].iter().collect();
                if let Some(colon) = decl_str.find(':') {
                    let prop = decl_str[..colon].trim().to_string();
                    let val = decl_str[colon + 1..].trim().to_string();
                    if !prop.is_empty() {
                        decls.push((prop, val));
                    }
                }
                decl_start = i + 1;
            }
            i += 1;
        }

        if decl_start < i {
            let decl_str: String = chars[decl_start..i].iter().collect();
            if let Some(colon) = decl_str.find(':') {
                let prop = decl_str[..colon].trim().to_string();
                let val = decl_str[colon + 1..].trim().to_string();
                if !prop.is_empty() {
                    decls.push((prop, val));
                }
            }
        }

        if i < len && chars[i] == '}' {
            i += 1;
        }

        if !selector.is_empty() {
            rules.push(CssRule {
                selector,
                declarations: decls,
                is_media: false,
                is_comment: false,
                media_query: String::new(),
                inner_rules: Vec::new(),
            });
        }
    }

    rules
}

pub fn process_css_rule(
    ctx: &mut GeneratorContext,
    structural_defs: &FxHashMap<String, BlockDef>,
    rule: &CssRule,
    is_root: bool,
    stack_index: i32,
) -> i32 {
    let rule_id = ctx.next_id();

    if rule.is_comment {
        let def = structural_defs.get("commentBlockCss");
        let block = DragWebBlockBean {
            id: rule_id.to_string(),
            op_code: "commentBlockCss".to_string(),
            spec: def.map(|d| d.spec.clone()).unwrap_or_else(|| "// %s".to_string()),
            spec2: String::new(),
            block_type: " ".to_string(),
            category: def.map(|d| d.category.clone()).unwrap_or_else(|| "comment".to_string()),
            color: def.map(|d| d.color).unwrap_or(-11243910), // #546E7A
            code: def.map(|d| d.code.clone()).unwrap_or_else(|| "/* %1$s */\n".to_string()),
            parameters: vec![rule.media_query.clone()],
            param_types: vec!["s".to_string()],
            sub_stack1: -1,
            sub_stack2: -1,
            next_block: -1,
            stack_index,
            is_connected_to_root: is_root,
            x: if is_root { 100 } else { 0 },
            y: if is_root { 100 } else { 0 },
            header_text: String::new(),
            tokenized_spec: vec!["s".to_string()],
        };
        ctx.add_block(block);
        return rule_id;
    }

    if rule.is_media {
        let mut first_inner_id = -1;
        let mut prev_inner_id = -1;

        for r in &rule.inner_rules {
            let id = process_css_rule(ctx, structural_defs, r, false, -1);
            if id >= 0 {
                if first_inner_id == -1 {
                    first_inner_id = id;
                }
                if prev_inner_id != -1 {
                    ctx.set_next_block(prev_inner_id, id);
                }
                prev_inner_id = id;
            }
        }

        let def = structural_defs.get("cssMediaQuery");
        let spec = def.map(|d| d.spec.clone()).unwrap_or_else(|| "@media (%s) {".to_string());
        let media_block = DragWebBlockBean {
            id: rule_id.to_string(),
            op_code: "cssMediaQuery".to_string(),
            spec: spec.clone(),
            spec2: String::new(),
            block_type: def.map(|d| d.block_type.clone()).unwrap_or_else(|| "c".to_string()),
            category: def.map(|d| d.category.clone()).unwrap_or_else(|| "responsive".to_string()),
            color: def.map(|d| d.color).unwrap_or(-15108462), // #1976D2
            code: def.map(|d| d.code.clone()).unwrap_or_else(|| "@media (%1$s) {\n %2$s\n}\n".to_string()),
            parameters: vec![rule.media_query.clone()],
            param_types: parse_spec_param_types(&spec),
            sub_stack1: first_inner_id,
            sub_stack2: -1,
            next_block: -1,
            stack_index,
            is_connected_to_root: is_root,
            x: if is_root { 100 } else { 0 },
            y: if is_root { 100 } else { 0 },
            header_text: String::new(),
            tokenized_spec: parse_spec_param_types(&spec),
        };

        ctx.add_block(media_block);
        return rule_id;
    }

    let mut first_decl_id = -1;
    let mut prev_decl_id = -1;

    for (prop, val) in &rule.declarations {
        let decl_str = format!("{}: {}", prop, val);
        let blocks = unpack_style_properties(&decl_str, ctx);
        for b in blocks {
            let bid: i32 = b.id.parse().unwrap_or(-1);
            if first_decl_id == -1 {
                first_decl_id = bid;
            }
            if prev_decl_id != -1 {
                ctx.set_next_block(prev_decl_id, bid);
            }
            prev_decl_id = bid;
            ctx.add_block(b);
        }
    }

    let (target_op, fallback_spec, fallback_code, params) = if rule.selector.ends_with(":hover") {
        let cleaned = rule.selector.trim_end_matches(":hover").trim().to_string();
        (
            "cssHover",
            "%selector hover",
            "%1$s:hover {\n %2$s\n}\n",
            vec![cleaned],
        )
    } else if rule.selector.ends_with(":focus") {
        let cleaned = rule.selector.trim_end_matches(":focus").trim().to_string();
        (
            "cssFocus",
            "%selector focus",
            "%1$s:focus {\n %2$s\n}\n",
            vec![cleaned],
        )
    } else if rule.selector.ends_with(":active") {
        let cleaned = rule.selector.trim_end_matches(":active").trim().to_string();
        (
            "cssActive",
            "%selector active",
            "%1$s:active {\n %2$s\n}\n",
            vec![cleaned],
        )
    } else if let Some(colon_idx) = rule.selector.find(':') {
        if !rule.selector.contains(' ') && structural_defs.contains_key("cssSelectorS") {
            let base = rule.selector[..colon_idx].trim().to_string();
            let pseudo = rule.selector[colon_idx + 1..].trim().to_string();
            (
                "cssSelectorS",
                "Widget %selector : %s.inputOnly",
                "%1$s%2$s {\n %3$s\n}\n",
                vec![base, pseudo],
            )
        } else {
            (
                "cssSelector",
                "Widget %selector",
                "%1$s {\n %2$s\n}\n",
                vec![rule.selector.clone()],
            )
        }
    } else {
        (
            "cssSelector",
            "Widget %selector",
            "%1$s {\n %2$s\n}\n",
            vec![rule.selector.clone()],
        )
    };

    let def = structural_defs.get(target_op);
    let spec = def.map(|d| d.spec.clone()).unwrap_or_else(|| fallback_spec.to_string());
    let ptypes = parse_spec_param_types(&spec);
    let sel_block = DragWebBlockBean {
        id: rule_id.to_string(),
        op_code: target_op.to_string(),
        spec,
        spec2: String::new(),
        block_type: def.map(|d| d.block_type.clone()).unwrap_or_else(|| "c".to_string()),
        category: def.map(|d| d.category.clone()).unwrap_or_else(|| "css".to_string()),
        color: def.map(|d| d.color).unwrap_or(-15108462), // #1976D2
        code: def.map(|d| d.code.clone()).unwrap_or_else(|| fallback_code.to_string()),
        parameters: params,
        param_types: ptypes.clone(),
        sub_stack1: first_decl_id,
        sub_stack2: -1,
        next_block: -1,
        stack_index,
        is_connected_to_root: is_root,
        x: if is_root { 100 } else { 0 },
        y: if is_root { 100 } else { 0 },
        header_text: String::new(),
        tokenized_spec: ptypes,
    };

    ctx.add_block(sel_block);
    rule_id
}

pub fn generate_css_blocks(css_code: &str, blocks_json: &str) -> Vec<DragWebBlockBean> {
    let (tag_map, attr_map, fb_cont, fb_leaf, fb_attr, fb_text, fb_comm, css_prop_map) =
        load_definitions(blocks_json);
    let structural_defs = load_css_structural_defs(blocks_json);

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

    let rules = parse_css_rules(css_code);
    let mut prev_rule_id = -1;
    let mut root_idx = 0;

    for rule in rules.iter() {
        let is_root = prev_rule_id == -1;
        let rule_id = process_css_rule(&mut ctx, &structural_defs, rule, is_root, root_idx);
        if rule_id >= 0 {
            if prev_rule_id != -1 {
                ctx.set_next_block(prev_rule_id, rule_id);
            }
            prev_rule_id = rule_id;
            root_idx += 1;
        }
    }

    ctx.blocks
}
