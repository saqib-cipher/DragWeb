use crate::generators::html::html_blocks_generator::{load_definitions, unpack_style_properties, GeneratorContext};
use crate::models::DragWebBlockBean;

#[derive(Clone, Debug)]
pub struct CssRule {
    pub selector: String,
    pub declarations: Vec<(String, String)>,
    pub is_media: bool,
    pub media_query: String,
    pub inner_rules: Vec<CssRule>,
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

        // Check for comments
        if chars[i] == '/' && i + 1 < len && chars[i + 1] == '*' {
            i += 2;
            while i + 1 < len && !(chars[i] == '*' && chars[i + 1] == '/') {
                i += 1;
            }
            i += 2;
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
            let inner_css: String = chars[inner_start..i - 1].iter().collect();
            let inner_rules = parse_css_rules(&inner_css);
            rules.push(CssRule {
                selector,
                declarations: Vec::new(),
                is_media: true,
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
                media_query: String::new(),
                inner_rules: Vec::new(),
            });
        }
    }

    rules
}

pub fn process_css_rule(ctx: &mut GeneratorContext, rule: &CssRule, is_root: bool) -> i32 {
    let rule_id = ctx.next_id();

    if rule.is_media {
        let mut first_inner_id = -1;
        let mut prev_inner_id = -1;

        for r in &rule.inner_rules {
            let id = process_css_rule(ctx, r, false);
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

        let media_block = DragWebBlockBean {
            id: rule_id.to_string(),
            op_code: "cssMediaQuery".to_string(),
            spec: "@media (%s) {".to_string(),
            spec2: String::new(),
            block_type: "c".to_string(),
            category: "Selectors".to_string(),
            color: -15108462, // #1976D2
            code: "@media (%1$s) {\n %2$s\n}\n".to_string(),
            parameters: vec![rule.media_query.clone()],
            param_types: vec!["s".to_string()],
            sub_stack1: first_inner_id,
            sub_stack2: -1,
            next_block: -1,
            stack_index: if is_root { 0 } else { -1 },
            is_connected_to_root: is_root,
            x: if is_root { 100 } else { 0 },
            y: if is_root { 100 } else { 0 },
            header_text: String::new(),
            tokenized_spec: vec!["s".to_string()],
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

    let (op_code, spec, code) = if rule.selector.contains(":hover") {
        (
            "cssHover".to_string(),
            "%selector hover".to_string(),
            "%1$s:hover {\n %2$s\n}\n".to_string(),
        )
    } else if rule.selector.contains(":focus") {
        (
            "cssFocus".to_string(),
            "%selector focus".to_string(),
            "%1$s:focus {\n %2$s\n}\n".to_string(),
        )
    } else if rule.selector.contains(":active") {
        (
            "cssActive".to_string(),
            "%selector active".to_string(),
            "%1$s:active {\n %2$s\n}\n".to_string(),
        )
    } else {
        (
            "cssSelector".to_string(),
            "Widget %selector".to_string(),
            "%1$s {\n %2$s\n}\n".to_string(),
        )
    };

    let cleaned_sel = rule
        .selector
        .replace(":hover", "")
        .replace(":focus", "")
        .replace(":active", "")
        .trim()
        .to_string();

    let sel_block = DragWebBlockBean {
        id: rule_id.to_string(),
        op_code,
        spec,
        spec2: String::new(),
        block_type: "c".to_string(),
        category: "Selectors".to_string(),
        color: -15108462, // #1976D2
        code,
        parameters: vec![cleaned_sel],
        param_types: vec!["selector".to_string()],
        sub_stack1: first_decl_id,
        sub_stack2: -1,
        next_block: -1,
        stack_index: if is_root { 0 } else { -1 },
        is_connected_to_root: is_root,
        x: if is_root { 100 } else { 0 },
        y: if is_root { 100 } else { 0 },
        header_text: String::new(),
        tokenized_spec: vec!["selector".to_string()],
    };

    ctx.add_block(sel_block);
    rule_id
}

pub fn generate_css_blocks(css_code: &str, blocks_json: &str) -> Vec<DragWebBlockBean> {
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

    let rules = parse_css_rules(css_code);
    let mut prev_rule_id = -1;

    for (idx, rule) in rules.iter().enumerate() {
        let is_root = idx == 0;
        let rule_id = process_css_rule(&mut ctx, rule, is_root);
        if rule_id >= 0 {
            if prev_rule_id != -1 {
                ctx.set_next_block(prev_rule_id, rule_id);
            }
            prev_rule_id = rule_id;
        }
    }

    ctx.blocks
}
