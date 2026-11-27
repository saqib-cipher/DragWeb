use crate::generators::html::html_blocks_generator::{
    load_definitions, unpack_style_properties, BlockDef, GeneratorContext,
};
use crate::models::{parse_hex_color_i32, parse_spec_param_types, DragWebBlockBean};
use rustc_hash::FxHashMap;
use serde_json::Value;

#[derive(Clone, Debug)]
pub enum CssBodyItem {
    Decl(String, String),
    Comment(String),
}

#[derive(Clone, Debug)]
pub struct CssRule {
    pub selector: String,
    pub body_items: Vec<CssBodyItem>,
    pub is_block_at_rule: bool,
    pub is_comment: bool,
    pub is_raw_asd: bool,
    pub comment_text: String,
    pub raw_code: String,
    pub inner_rules: Vec<CssRule>,
}

#[derive(Clone, Debug)]
struct CssTemplateMatcher {
    def: BlockDef,
    segments: Vec<String>,
    param_indices: Vec<usize>,
    literal_score: usize,
}

struct CssJsonCatalog {
    by_id: FxHashMap<String, BlockDef>,
    container_headers: Vec<CssTemplateMatcher>,
    decl_matchers: Vec<CssTemplateMatcher>,
}

fn parse_code_template(raw_code: &str) -> (Vec<String>, Vec<usize>, usize) {
    let cleaned = raw_code.trim().trim_end_matches(';').trim();
    let chars: Vec<char> = cleaned.chars().collect();
    let len = chars.len();
    let mut i = 0;
    let mut segments = Vec::new();
    let mut indices = Vec::new();
    let mut curr_lit = String::new();
    let mut next_auto = 1usize;

    while i < len {
        if chars[i] == '%' && i + 1 < len {
            if chars[i + 1].is_ascii_digit() {
                let mut d_end = i + 1;
                while d_end < len && chars[d_end].is_ascii_digit() {
                    d_end += 1;
                }
                if d_end + 1 < len
                    && chars[d_end] == '$'
                    && (chars[d_end + 1] == 's' || chars[d_end + 1] == 'd')
                {
                    let idx_num: usize = chars[i + 1..d_end]
                        .iter()
                        .collect::<String>()
                        .parse()
                        .unwrap_or(next_auto);
                    segments.push(curr_lit.clone());
                    curr_lit.clear();
                    indices.push(idx_num);
                    next_auto = idx_num + 1;
                    i = d_end + 2;
                    continue;
                }
            }
            if chars[i + 1] == 's'
                || chars[i + 1] == 'd'
                || chars[i + 1] == 'b'
                || chars[i + 1] == 'n'
            {
                segments.push(curr_lit.clone());
                curr_lit.clear();
                indices.push(next_auto);
                next_auto += 1;
                i += 2;
                continue;
            }
        }
        curr_lit.push(chars[i]);
        i += 1;
    }
    segments.push(curr_lit);
    let score: usize = segments.iter().map(|s| s.trim().len()).sum();
    (segments, indices, score)
}

fn match_flex_prefix(input: &str, pos: usize, pat: &str) -> Option<usize> {
    let ib = input.as_bytes();
    let pb = pat.as_bytes();
    let mut ii = pos;
    let mut pi = 0;
    while pi < pb.len() {
        let pc = pb[pi];
        if pc.is_ascii_whitespace() {
            while pi < pb.len() && pb[pi].is_ascii_whitespace() {
                pi += 1;
            }
            while ii < ib.len() && ib[ii].is_ascii_whitespace() {
                ii += 1;
            }
            continue;
        }
        if ii >= ib.len() {
            return None;
        }
        let ic = ib[ii];
        let eq = if (pc == b'"' || pc == b'\'') && (ic == b'"' || ic == b'\'') {
            true
        } else {
            pc.eq_ignore_ascii_case(&ic)
        };
        if !eq {
            return None;
        }
        ii += 1;
        pi += 1;
    }
    Some(ii)
}

fn find_flex_pos(input: &str, start: usize, pat: &str) -> Option<(usize, usize)> {
    if pat.is_empty() {
        return Some((start, start));
    }
    for (idx, _) in input[start..].char_indices() {
        let abs_idx = start + idx;
        if let Some(end) = match_flex_prefix(input, abs_idx, pat) {
            return Some((abs_idx, end));
        }
    }
    None
}

fn match_template(input: &str, m: &CssTemplateMatcher) -> Option<Vec<String>> {
    if m.segments.is_empty() {
        return None;
    }
    let clean = input.trim().trim_end_matches(';').trim();
    if clean.is_empty() {
        return None;
    }
    let mut pos = match_flex_prefix(clean, 0, &m.segments[0])?;
    if m.param_indices.is_empty() {
        if clean[pos..].trim().is_empty() {
            return Some(Vec::new());
        }
        return None;
    }

    let max_idx = *m.param_indices.iter().max().unwrap_or(&0);
    let mut params = vec![String::new(); max_idx];
    for k in 0..m.param_indices.len() {
        let next_seg = &m.segments[k + 1];
        let slot = m.param_indices[k].saturating_sub(1);
        if next_seg.is_empty() {
            if k + 1 == m.param_indices.len() {
                params[slot] = clean[pos..].trim().to_string();
                pos = clean.len();
            } else {
                return None;
            }
        } else {
            let (found_start, found_end) = find_flex_pos(clean, pos, next_seg)?;
            let val = clean[pos..found_start].trim();
            if val.is_empty() && next_seg.trim().len() > 1 {
                return None;
            }
            params[slot] = val.to_string();
            pos = found_end;
        }
    }
    if !clean[pos..].trim().is_empty() {
        return None;
    }
    Some(params)
}

fn load_css_json_catalog(blocks_json: &str) -> CssJsonCatalog {
    let mut by_id = FxHashMap::default();
    let mut container_headers = Vec::new();
    let mut decl_matchers = Vec::new();

    if let Ok(Value::Array(items)) = serde_json::from_str::<Value>(blocks_json) {
        for item in items {
            let id = item
                .get("id")
                .or_else(|| item.get("name"))
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            let cat = item
                .get("category")
                .or_else(|| item.get("palette"))
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            if id.is_empty() {
                continue;
            }
            let cat_l = cat.to_ascii_lowercase();
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
            let spec = item
                .get("spec")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            let spec2 = item
                .get("spec2")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            let code = item
                .get("code")
                .or_else(|| item.get("template"))
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();

            let is_css_related = cat_l.starts_with("css")
                || cat_l == "responsive"
                || cat_l == "comment"
                || cat_l == "asd"
                || id.starts_with("css")
                || id.starts_with("commentBlock")
                || id.starts_with("asd")
                || (!cat_l.starts_with("js")
                    && !cat_l.starts_with("html")
                    && cat_l != "control"
                    && cat_l != "operator"
                    && cat_l != "widgets"
                    && code.contains(':')
                    && code.contains(';'));

            if !is_css_related {
                continue;
            }

            let def = BlockDef {
                id: id.clone(),
                category: cat.clone(),
                block_type: block_type.clone(),
                color: parse_hex_color_i32(color_str),
                spec,
                spec2,
                code: code.clone(),
            };
            by_id.insert(id.clone(), def.clone());

            // Container block header template (e.g. "%1$s:hover {", "@media (max-width: %1$s) {", "@keyframes %1$s {")
            if (block_type == "c" || block_type == "e") && code.contains('{') {
                if let Some(brace_pos) = code.find('{') {
                    let header_code = code[..brace_pos].trim();
                    let (segments, param_indices, literal_score) = parse_code_template(header_code);
                    if !segments.is_empty() {
                        container_headers.push(CssTemplateMatcher {
                            def: def.clone(),
                            segments,
                            param_indices,
                            literal_score,
                        });
                    }
                }
            } else if block_type == " " && code.contains(':') && code.contains(';') && !code.contains('{') {
                let (segments, param_indices, literal_score) = parse_code_template(&code);
                if literal_score >= 3 {
                    decl_matchers.push(CssTemplateMatcher {
                        def,
                        segments,
                        param_indices,
                        literal_score,
                    });
                }
            }
        }
    }

    container_headers.sort_by(|a, b| {
        b.literal_score
            .cmp(&a.literal_score)
            .then_with(|| b.param_indices.len().cmp(&a.param_indices.len()))
    });
    decl_matchers.sort_by(|a, b| {
        b.literal_score
            .cmp(&a.literal_score)
            .then_with(|| b.param_indices.len().cmp(&a.param_indices.len()))
    });

    CssJsonCatalog {
        by_id,
        container_headers,
        decl_matchers,
    }
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
            while comment_end + 1 < len
                && !(chars[comment_end] == '*' && chars[comment_end + 1] == '/')
            {
                comment_end += 1;
            }
            let comment_text: String = chars[comment_start..comment_end].iter().collect();
            let trimmed = comment_text.trim().to_string();
            if !trimmed.is_empty() {
                rules.push(CssRule {
                    selector: String::new(),
                    body_items: Vec::new(),
                    is_block_at_rule: false,
                    is_comment: true,
                    is_raw_asd: false,
                    comment_text: trimmed,
                    raw_code: String::new(),
                    inner_rules: Vec::new(),
                });
            }
            i = if comment_end + 1 < len {
                comment_end + 2
            } else {
                len
            };
            continue;
        }

        // At-directives terminated by semicolon before '{' (e.g. @import url("theme.css");)
        if chars[i] == '@' {
            let mut look = i;
            let mut semi_pos = None;
            while look < len {
                if chars[look] == ';' {
                    semi_pos = Some(look);
                    break;
                }
                if chars[look] == '{' {
                    break;
                }
                look += 1;
            }
            if let Some(sp) = semi_pos {
                let directive: String = chars[i..=sp].iter().collect();
                let trimmed = directive.trim().to_string();
                i = sp + 1;
                // Skip DragWeb auto-inserted theme import; keep explicit other at-directives as ASD
                let lower = trimmed.to_ascii_lowercase();
                if lower.starts_with("@import") && lower.contains("theme.css") {
                    continue;
                }
                if !trimmed.is_empty() {
                    rules.push(CssRule {
                        selector: String::new(),
                        body_items: Vec::new(),
                        is_block_at_rule: false,
                        is_comment: false,
                        is_raw_asd: true,
                        comment_text: String::new(),
                        raw_code: trimmed,
                        inner_rules: Vec::new(),
                    });
                }
                continue;
            }
        }

        // Read selector / at-rule header up to '{' (preserving comments only outside)
        let sel_start = i;
        let mut in_str: Option<char> = None;
        while i < len {
            let c = chars[i];
            if let Some(q) = in_str {
                if c == q {
                    in_str = None;
                }
                i += 1;
                continue;
            }
            if c == '"' || c == '\'' {
                in_str = Some(c);
                i += 1;
                continue;
            }
            if c == '{' {
                break;
            }
            if c == ';' {
                // Unexpected stray statement outside braces -> preserve via ASD
                let raw: String = chars[sel_start..=i].iter().collect();
                let trimmed = raw.trim().to_string();
                if !trimmed.is_empty() {
                    rules.push(CssRule {
                        selector: String::new(),
                        body_items: Vec::new(),
                        is_block_at_rule: false,
                        is_comment: false,
                        is_raw_asd: true,
                        comment_text: String::new(),
                        raw_code: trimmed,
                        inner_rules: Vec::new(),
                    });
                }
                i += 1;
                break;
            }
            i += 1;
        }
        if i >= len || chars[i] != '{' {
            if sel_start < i && (i >= len) {
                let leftover: String = chars[sel_start..i].iter().collect();
                let trimmed = leftover.trim().to_string();
                if !trimmed.is_empty() {
                    rules.push(CssRule {
                        selector: String::new(),
                        body_items: Vec::new(),
                        is_block_at_rule: false,
                        is_comment: false,
                        is_raw_asd: true,
                        comment_text: String::new(),
                        raw_code: trimmed,
                        inner_rules: Vec::new(),
                    });
                }
            }
            continue;
        }

        let selector: String = chars[sel_start..i].iter().collect();
        let selector = selector.trim().to_string();
        i += 1; // skip '{'

        // Find matching closing '}' accounting for nested braces/strings/comments
        let inner_start = i;
        let mut brace_depth = 1;
        let mut has_nested_braces = false;
        let mut body_in_str: Option<char> = None;
        while i < len && brace_depth > 0 {
            let c = chars[i];
            if let Some(q) = body_in_str {
                if c == q {
                    body_in_str = None;
                }
                i += 1;
                continue;
            }
            if c == '"' || c == '\'' {
                body_in_str = Some(c);
                i += 1;
                continue;
            }
            if c == '/' && i + 1 < len && chars[i + 1] == '*' {
                i += 2;
                while i + 1 < len && !(chars[i] == '*' && chars[i + 1] == '/') {
                    i += 1;
                }
                i = if i + 1 < len { i + 2 } else { len };
                continue;
            }
            if c == '{' {
                brace_depth += 1;
                has_nested_braces = true;
            } else if c == '}' {
                brace_depth -= 1;
            }
            i += 1;
        }

        let end_slice = if brace_depth == 0 && i > 0 { i - 1 } else { len };
        let inner_css: String = chars[inner_start..end_slice].iter().collect();

        let sel_lower = selector.to_ascii_lowercase();
        let is_nested_at = sel_lower.starts_with("@media")
            || sel_lower.starts_with("@keyframes")
            || sel_lower.starts_with("@-webkit-keyframes")
            || sel_lower.starts_with("@supports")
            || sel_lower.starts_with("@layer")
            || has_nested_braces;

        if is_nested_at && has_nested_braces {
            let inner_rules = parse_css_rules(&inner_css);
            rules.push(CssRule {
                selector,
                body_items: Vec::new(),
                is_block_at_rule: true,
                is_comment: false,
                is_raw_asd: false,
                comment_text: String::new(),
                raw_code: inner_css,
                inner_rules,
            });
            continue;
        }

        // Parse declaration body preserving inline comments in sequence
        let body_items = parse_declaration_body(&inner_css);
        if !selector.is_empty() {
            rules.push(CssRule {
                selector,
                body_items,
                is_block_at_rule: sel_lower.starts_with('@'),
                is_comment: false,
                is_raw_asd: false,
                comment_text: String::new(),
                raw_code: inner_css,
                inner_rules: Vec::new(),
            });
        }
    }

    rules
}

fn parse_declaration_body(body: &str) -> Vec<CssBodyItem> {
    let mut items = Vec::new();
    let chars: Vec<char> = body.chars().collect();
    let len = chars.len();
    let mut i = 0;
    let mut start = 0;
    let mut paren_depth = 0;
    let mut in_str: Option<char> = None;

    while i < len {
        let c = chars[i];
        if let Some(q) = in_str {
            if c == q {
                in_str = None;
            }
            i += 1;
            continue;
        }
        if c == '"' || c == '\'' {
            in_str = Some(c);
            i += 1;
            continue;
        }
        if c == '(' {
            paren_depth += 1;
            i += 1;
            continue;
        }
        if c == ')' && paren_depth > 0 {
            paren_depth -= 1;
            i += 1;
            continue;
        }
        if paren_depth == 0 && c == '/' && i + 1 < len && chars[i + 1] == '*' {
            // Flush prior partial declaration if any
            if start < i {
                let prev: String = chars[start..i].iter().collect();
                if let Some(colon) = prev.find(':') {
                    let prop = prev[..colon].trim().to_string();
                    let val = prev[colon + 1..].trim().to_string();
                    if !prop.is_empty() {
                        items.push(CssBodyItem::Decl(prop, val));
                    }
                }
            }
            let cs = i + 2;
            let mut ce = cs;
            while ce + 1 < len && !(chars[ce] == '*' && chars[ce + 1] == '/') {
                ce += 1;
            }
            let txt: String = chars[cs..ce].iter().collect();
            let trimmed = txt.trim().to_string();
            if !trimmed.is_empty() {
                items.push(CssBodyItem::Comment(trimmed));
            }
            i = if ce + 1 < len { ce + 2 } else { len };
            start = i;
            continue;
        }
        if paren_depth == 0 && c == ';' {
            let decl_str: String = chars[start..i].iter().collect();
            if let Some(colon) = decl_str.find(':') {
                let prop = decl_str[..colon].trim().to_string();
                let val = decl_str[colon + 1..].trim().to_string();
                if !prop.is_empty() {
                    items.push(CssBodyItem::Decl(prop, val));
                }
            }
            start = i + 1;
        }
        i += 1;
    }

    if start < len {
        let decl_str: String = chars[start..len].iter().collect();
        if let Some(colon) = decl_str.find(':') {
            let prop = decl_str[..colon].trim().to_string();
            let val = decl_str[colon + 1..].trim().to_string();
            if !prop.is_empty() {
                items.push(CssBodyItem::Decl(prop, val));
            }
        }
    }

    items
}

fn make_bean_from_def(
    id: i32,
    def: &BlockDef,
    params: Vec<String>,
    sub1: i32,
    is_root: bool,
    stack_index: i32,
) -> DragWebBlockBean {
    let ptypes = parse_spec_param_types(&def.spec);
    DragWebBlockBean {
        id: id.to_string(),
        op_code: def.id.clone(),
        spec: def.spec.clone(),
        spec2: def.spec2.clone(),
        block_type: def.block_type.clone(),
        category: def.category.clone(),
        color: def.color,
        code: def.code.clone(),
        parameters: params,
        param_types: ptypes.clone(),
        sub_stack1: sub1,
        sub_stack2: -1,
        next_block: -1,
        stack_index,
        is_connected_to_root: is_root,
        x: if is_root { 100 } else { 0 },
        y: if is_root { 100 } else { 0 },
        header_text: String::new(),
        tokenized_spec: ptypes,
    }
}

fn build_asd_block(
    ctx: &mut GeneratorContext,
    catalog: &CssJsonCatalog,
    raw_code: &str,
    is_root: bool,
    stack_index: i32,
) -> i32 {
    let bid = ctx.next_id();
    let cleaned = raw_code.trim().to_string();
    let bean = if let Some(def) = catalog
        .by_id
        .get("asdN")
        .or_else(|| catalog.by_id.get("asdCss"))
    {
        let mut b = make_bean_from_def(bid, def, vec![cleaned], -1, is_root, stack_index);
        if b.block_type.is_empty() {
            b.block_type = " ".to_string();
        }
        b
    } else {
        DragWebBlockBean {
            id: bid.to_string(),
            op_code: "asdN".to_string(),
            spec: "Add source directly %s.inputOnly".to_string(),
            spec2: String::new(),
            block_type: " ".to_string(),
            category: "asd".to_string(),
            color: -12232092,
            code: "%1$s\n".to_string(),
            parameters: vec![cleaned],
            param_types: vec!["s.inputOnly".to_string()],
            sub_stack1: -1,
            sub_stack2: -1,
            next_block: -1,
            stack_index,
            is_connected_to_root: is_root,
            x: if is_root { 100 } else { 0 },
            y: if is_root { 100 } else { 0 },
            header_text: String::new(),
            tokenized_spec: vec!["s.inputOnly".to_string()],
        }
    };
    ctx.add_block(bean);
    bid
}

fn build_comment_block(
    ctx: &mut GeneratorContext,
    catalog: &CssJsonCatalog,
    raw_comment: &str,
    is_root: bool,
    stack_index: i32,
) -> i32 {
    let bid = ctx.next_id();
    let trimmed = raw_comment.trim();
    // Detect /*===== Title =====*/ -> commentBlockBold if available in JSON
    if trimmed.starts_with("=====") && trimmed.ends_with("=====") && trimmed.len() > 10 {
        let inner = trimmed
            .trim_start_matches('=')
            .trim_end_matches('=')
            .trim()
            .to_string();
        if let Some(def) = catalog.by_id.get("commentBlockBold") {
            let b = make_bean_from_def(bid, def, vec![inner], -1, is_root, stack_index);
            ctx.add_block(b);
            return bid;
        }
    }

    if let Some(def) = catalog.by_id.get("commentBlockCss") {
        let b = make_bean_from_def(
            bid,
            def,
            vec![trimmed.to_string()],
            -1,
            is_root,
            stack_index,
        );
        ctx.add_block(b);
        return bid;
    }

    let bean = DragWebBlockBean {
        id: bid.to_string(),
        op_code: "commentBlockCss".to_string(),
        spec: "//Comment %s.inputOnly".to_string(),
        spec2: String::new(),
        block_type: "c".to_string(),
        category: "comment".to_string(),
        color: -11243910,
        code: "/* %1$s */ %2$s".to_string(),
        parameters: vec![trimmed.to_string()],
        param_types: vec!["s.inputOnly".to_string()],
        sub_stack1: -1,
        sub_stack2: -1,
        next_block: -1,
        stack_index,
        is_connected_to_root: is_root,
        x: if is_root { 100 } else { 0 },
        y: if is_root { 100 } else { 0 },
        header_text: String::new(),
        tokenized_spec: vec!["s.inputOnly".to_string()],
    };
    ctx.add_block(bean);
    bid
}

fn split_num_unit_token(tok: &str) -> Option<(String, String)> {
    let t = tok.trim();
    if t.is_empty() {
        return None;
    }
    if t == "0" {
        return Some(("0".to_string(), "px".to_string()));
    }
    let mut split_idx = 0;
    for (idx, c) in t.char_indices() {
        if c.is_ascii_digit() || c == '.' || (idx == 0 && (c == '-' || c == '+')) {
            split_idx = idx + c.len_utf8();
        } else {
            break;
        }
    }
    if split_idx == 0 || split_idx >= t.len() {
        return None;
    }
    let num = t[..split_idx].to_string();
    let unit = t[split_idx..].to_string();
    if unit.chars().all(|c| c.is_ascii_alphabetic() || c == '%') {
        Some((num, unit))
    } else {
        None
    }
}

/// Attempts to emit a `cssCUnits` / `cssCUnits2` / `cssCUnits3` child reporter block
/// from `blocks.json` for 2-, 3-, or 4-part spacing shorthand values (e.g. `10px 20px`).
fn try_build_css_unit_reporter(
    ctx: &mut GeneratorContext,
    catalog: &CssJsonCatalog,
    val: &str,
) -> Option<String> {
    let parts: Vec<&str> = val.split_whitespace().collect();
    let target_id = match parts.len() {
        2 => "cssCUnits",
        3 => "cssCUnits2",
        4 => "cssCUnits3",
        _ => return None,
    };
    let def = catalog.by_id.get(target_id)?;
    let mut params = Vec::with_capacity(parts.len() * 2);
    for p in parts {
        let (num, unit) = split_num_unit_token(p)?;
        params.push(num);
        params.push(unit);
    }
    let child_id = ctx.next_id();
    let bean = make_bean_from_def(child_id, def, params, -1, false, -1);
    ctx.add_block(bean);
    Some(format!("@{}", child_id))
}

/// Attempts to build a `cssControl` reporter block (and nested children) for selector suffix strings
/// like `:not(.active)`, `:nth-child(2n)`, `.active`, `:first-child`, `div + p`, etc.
fn try_build_selector_control_reporter(
    ctx: &mut GeneratorContext,
    catalog: &CssJsonCatalog,
    suffix: &str,
    depth: usize,
) -> Option<String> {
    if depth > 4 {
        return None;
    }
    let trimmed = suffix.trim();
    if trimmed.is_empty() {
        return None;
    }

    // 1. :not(inner) -> cssCNot
    if trimmed.starts_with(":not(") && trimmed.ends_with(')') {
        if let Some(def) = catalog.by_id.get("cssCNot") {
            let inner = trimmed[5..trimmed.len() - 1].trim().to_string();
            let cid = ctx.next_id();
            ctx.add_block(make_bean_from_def(cid, def, vec![inner], -1, false, -1));
            return Some(format!("@{}", cid));
        }
    }

    // 2. :nth-child(n) -> cssCNth
    if trimmed.starts_with(":nth-child(") && trimmed.ends_with(')') {
        if let Some(def) = catalog.by_id.get("cssCNth") {
            let inner = trimmed[11..trimmed.len() - 1].trim().to_string();
            let cid = ctx.next_id();
            ctx.add_block(make_bean_from_def(cid, def, vec![inner], -1, false, -1));
            return Some(format!("@{}", cid));
        }
    }

    // 3. Zero-arg cssControl reporter blocks matched via exact `code` from blocks.json
    let static_candidates = [
        "cssCFirst",
        "cssClast",
        "cssCHover",
        "cssCFocus",
        "cssCBefore",
        "cssCafter",
        "cssCVisited",
        "cssCActive",
    ];
    for op in static_candidates {
        if let Some(def) = catalog.by_id.get(op) {
            if def.code.trim().eq_ignore_ascii_case(trimmed) {
                let cid = ctx.next_id();
                ctx.add_block(make_bean_from_def(cid, def, Vec::new(), -1, false, -1));
                return Some(format!("@{}", cid));
            }
        }
    }

    // 4. Compound chained suffix like `.active:hover` or `:first-child:hover` -> cssCjoin
    if let Some(def_join) = catalog.by_id.get("cssCjoin") {
        // Split at an interior ':' or '.' if both parts match reporter blocks or clean fragments
        if let Some(split_pos) = trimmed[1..]
            .char_indices()
            .find(|&(_, ch)| ch == ':' || ch == '.')
            .map(|(idx, _)| idx + 1)
        {
            let left = trimmed[..split_pos].trim();
            let right = trimmed[split_pos..].trim();
            if !left.is_empty() && !right.is_empty() {
                let p1 = try_build_selector_control_reporter(ctx, catalog, left, depth + 1)
                    .unwrap_or_else(|| left.to_string());
                let p2 = try_build_selector_control_reporter(ctx, catalog, right, depth + 1)
                    .unwrap_or_else(|| right.to_string());
                let cid = ctx.next_id();
                ctx.add_block(make_bean_from_def(cid, def_join, vec![p1, p2], -1, false, -1));
                return Some(format!("@{}", cid));
            }
        }
    }

    None
}

/// Decomposes a CSS selector header into (block_id, parameters) using all container definitions
/// and `cssControl` reporter blocks loaded from `blocks.json`.
fn resolve_selector_block_and_params(
    ctx: &mut GeneratorContext,
    catalog: &CssJsonCatalog,
    selector: &str,
    in_keyframes: bool,
) -> Option<(BlockDef, Vec<String>)> {
    let trimmed = selector.trim();

    if in_keyframes {
        if let Some(def_kf_step) = catalog.by_id.get("cssKeyframeStep") {
            return Some((def_kf_step.clone(), vec![trimmed.to_string()]));
        }
    }

    // 1. Try matching specific container templates from blocks.json (excluding generic cssSelector / cssSelectorS / cssKeyframeStep)
    for m in &catalog.container_headers {
        let id = m.def.id.as_str();
        if id == "cssSelector" || id == "cssSelectorS" || id == "cssKeyframeStep" {
            continue;
        }
        // Only treat "@media ..." matcher as match if selector itself starts with '@'
        if m.segments.first().map(|s| s.trim_start().starts_with('@')).unwrap_or(false)
            && !trimmed.starts_with('@')
        {
            continue;
        }
        // cssNthChild, cssHover, cssFocus, etc.: base selector must not be empty and must not contain extra trailing combinators
        if let Some(params) = match_template(trimmed, m) {
            let non_empty_ok = params.iter().all(|p| !p.trim().is_empty());
            if non_empty_ok {
                return Some((m.def.clone(), params));
            }
        }
    }

    // 2. Try cssSelectorS + cssControl reporter stacking for pseudo / modifier selectors (e.g. `.btn:not(.x)`, `.item.active`, `.row:nth-child(2n)`)
    if let Some(def_sel_s) = catalog.by_id.get("cssSelectorS") {
        // Descendant space + pseudo/class suffix e.g. ".nav .item:hover" or base + pseudo ":not(...)"
        if let Some(colon_pos) = find_top_level_pseudo_split(trimmed) {
            let base = trimmed[..colon_pos].trim().to_string();
            let suffix = trimmed[colon_pos..].trim();
            if !base.is_empty() && !suffix.is_empty() {
                if let Some(ctrl_ref) =
                    try_build_selector_control_reporter(ctx, catalog, suffix, 0)
                {
                    return Some((def_sel_s.clone(), vec![base, ctrl_ref]));
                }
                let pseudo_no_colon = suffix.trim_start_matches(':').to_string();
                if !base.contains(' ') {
                    return Some((def_sel_s.clone(), vec![base, format!(":{}", pseudo_no_colon)]));
                }
            }
        }
    }

    // 3. Combinator selector stacking using cssControlS ("%selector : %s.inputOnly", code: " %1$s%2$s") or cssSelector
    if let Some(def_sel) = catalog.by_id.get("cssSelector") {
        return Some((def_sel.clone(), vec![trimmed.to_string()]));
    }

    None
}

fn find_top_level_pseudo_split(sel: &str) -> Option<usize> {
    let chars: Vec<char> = sel.chars().collect();
    let mut paren_depth = 0;
    let mut bracket_depth = 0;
    let mut last_pseudo_byte = None;
    let mut byte_idx = 0;
    for ch in chars {
        match ch {
            '(' => paren_depth += 1,
            ')' if paren_depth > 0 => paren_depth -= 1,
            '[' => bracket_depth += 1,
            ']' if bracket_depth > 0 => bracket_depth -= 1,
            ':' if paren_depth == 0 && bracket_depth == 0 => {
                // Handle `::before` / `::after`
                if last_pseudo_byte.is_none() || byte_idx > last_pseudo_byte.unwrap_or(0) + 1 {
                    last_pseudo_byte = Some(byte_idx);
                }
            }
            _ => {}
        }
        byte_idx += ch.len_utf8();
    }
    last_pseudo_byte
}

fn emit_declaration_blocks(
    ctx: &mut GeneratorContext,
    catalog: &CssJsonCatalog,
    body_items: &[CssBodyItem],
) -> i32 {
    let mut first_id = -1;
    let mut prev_id = -1;

    let mut link_block = |ctx: &mut GeneratorContext, bid: i32| {
        if bid >= 0 {
            if first_id == -1 {
                first_id = bid;
            }
            if prev_id != -1 {
                ctx.set_next_block(prev_id, bid);
            }
            prev_id = bid;
        }
    };

    for item in body_items {
        match item {
            CssBodyItem::Comment(text) => {
                let cid = build_comment_block(ctx, catalog, text, false, -1);
                link_block(ctx, cid);
            }
            CssBodyItem::Decl(prop, val) => {
                let decl_str = format!("{}: {}", prop, val);
                let mut unpacked = unpack_style_properties(&decl_str, ctx);
                let mut handled = false;

                if unpacked.len() == 1 && unpacked[0].op_code != "cssPropertyGeneric" {
                    let mut b = unpacked.remove(0);
                    // Upgrade setMarginMulti / setPaddingMulti when shorthand has 2-4 unit values to stack cssCUnits*
                    if (b.op_code == "setMarginMulti" || b.op_code == "setPaddingMulti")
                        && !b.parameters.is_empty()
                    {
                        if let Some(unit_ref) = try_build_css_unit_reporter(ctx, catalog, &b.parameters[0]) {
                            b.parameters[0] = unit_ref;
                        }
                    }
                    let bid: i32 = b.id.parse().unwrap_or(-1);
                    ctx.add_block(b);
                    link_block(ctx, bid);
                    handled = true;
                }

                // Try full JSON declaration template scan (supports custom user JSON blocks & animation/transform presets)
                if !handled {
                    for m in &catalog.decl_matchers {
                        if let Some(params) = match_template(&decl_str, m) {
                            let bid = ctx.next_id();
                            let bean = make_bean_from_def(bid, &m.def, params, -1, false, -1);
                            ctx.add_block(bean);
                            link_block(ctx, bid);
                            handled = true;
                            break;
                        }
                    }
                }

                // Fallback: emit Add Source Directly (`asdN`) so unknown CSS property stays editable & round-trips identically
                if !handled {
                    let bid = build_asd_block(
                        ctx,
                        catalog,
                        &format!("{}: {};", prop.trim(), val.trim()),
                        false,
                        -1,
                    );
                    link_block(ctx, bid);
                }
            }
        }
    }

    first_id
}

pub fn process_css_rule(
    ctx: &mut GeneratorContext,
    catalog_unused: &FxHashMap<String, BlockDef>,
    rule: &CssRule,
    is_root: bool,
    stack_index: i32,
) -> i32 {
    let empty_catalog = CssJsonCatalog {
        by_id: catalog_unused.clone(),
        container_headers: Vec::new(),
        decl_matchers: Vec::new(),
    };
    process_css_rule_with_catalog(ctx, &empty_catalog, rule, is_root, stack_index, false)
}

fn process_css_rule_with_catalog(
    ctx: &mut GeneratorContext,
    catalog: &CssJsonCatalog,
    rule: &CssRule,
    is_root: bool,
    stack_index: i32,
    in_keyframes: bool,
) -> i32 {
    if rule.is_comment {
        return build_comment_block(ctx, catalog, &rule.comment_text, is_root, stack_index);
    }

    if rule.is_raw_asd {
        return build_asd_block(ctx, catalog, &rule.raw_code, is_root, stack_index);
    }

    let sel_trimmed = rule.selector.trim();
    let sel_lower = sel_trimmed.to_ascii_lowercase();

    // Handle block at-rules (@media, @keyframes, @supports, etc.)
    if rule.is_block_at_rule {
        let child_in_kf = sel_lower.starts_with("@keyframes") || sel_lower.starts_with("@-webkit-keyframes");
        // Match against JSON container templates first (e.g. cssMediaQuery, cssKeyframes)
        for m in &catalog.container_headers {
            if !m
                .segments
                .first()
                .map(|s| s.trim_start().starts_with('@'))
                .unwrap_or(false)
            {
                continue;
            }
            if let Some(params) = match_template(sel_trimmed, m) {
                let mut first_inner_id = -1;
                let mut prev_inner_id = -1;
                for r in &rule.inner_rules {
                    let id = process_css_rule_with_catalog(ctx, catalog, r, false, -1, child_in_kf);
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
                let rule_id = ctx.next_id();
                let block = make_bean_from_def(
                    rule_id,
                    &m.def,
                    params,
                    first_inner_id,
                    is_root,
                    stack_index,
                );
                ctx.add_block(block);
                return rule_id;
            }
        }

        // Legacy/test fallback when blocks.json is empty ("[]") for @media (...)
        if sel_lower.starts_with("@media") && catalog.by_id.is_empty() {
            let mut first_inner_id = -1;
            let mut prev_inner_id = -1;
            for r in &rule.inner_rules {
                let id = process_css_rule_with_catalog(ctx, catalog, r, false, -1, false);
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
            let rule_id = ctx.next_id();
            let mq = sel_trimmed.trim_start_matches("@media").trim().to_string();
            let media_block = DragWebBlockBean {
                id: rule_id.to_string(),
                op_code: "cssMediaQuery".to_string(),
                spec: "@media max-width %d".to_string(),
                spec2: String::new(),
                block_type: "c".to_string(),
                category: "responsive".to_string(),
                color: -15108462,
                code: "@media (max-width: %1$s) {\n%2$s\n}".to_string(),
                parameters: vec![mq],
                param_types: vec!["d".to_string()],
                sub_stack1: first_inner_id,
                sub_stack2: -1,
                next_block: -1,
                stack_index,
                is_connected_to_root: is_root,
                x: if is_root { 100 } else { 0 },
                y: if is_root { 100 } else { 0 },
                header_text: String::new(),
                tokenized_spec: vec!["d".to_string()],
            };
            ctx.add_block(media_block);
            return rule_id;
        }

        // Non-matching at-rule (e.g. @media (min-width: ...), @font-face, @supports): emit Add Source Directly (`asdN`)
        let raw_rule = format!("{} {{\n{}\n}}", sel_trimmed, rule.raw_code.trim());
        return build_asd_block(ctx, catalog, &raw_rule, is_root, stack_index);
    }

    // Standard selector / keyframe-step rule
    let first_decl_id = emit_declaration_blocks(ctx, catalog, &rule.body_items);

    if let Some((def, params)) =
        resolve_selector_block_and_params(ctx, catalog, sel_trimmed, in_keyframes)
    {
        let rule_id = ctx.next_id();
        let sel_block =
            make_bean_from_def(rule_id, &def, params, first_decl_id, is_root, stack_index);
        ctx.add_block(sel_block);
        return rule_id;
    }

    // Fallback when blocks.json is empty (unit tests)
    let rule_id = ctx.next_id();
    let (target_op, fallback_spec, fallback_code, params) = if sel_trimmed.ends_with(":hover") {
        let cleaned = sel_trimmed.trim_end_matches(":hover").trim().to_string();
        ("cssHover", "%selector hover", "%1$s:hover {\n %2$s\n}\n", vec![cleaned])
    } else if sel_trimmed.ends_with(":focus") {
        let cleaned = sel_trimmed.trim_end_matches(":focus").trim().to_string();
        ("cssFocus", "%selector focus", "%1$s:focus {\n %2$s\n}\n", vec![cleaned])
    } else if sel_trimmed.ends_with(":active") {
        let cleaned = sel_trimmed.trim_end_matches(":active").trim().to_string();
        ("cssActive", "%selector active", "%1$s:active {\n %2$s\n}\n", vec![cleaned])
    } else {
        ("cssSelector", "Widget %selector", "%1$s {\n %2$s\n}\n", vec![sel_trimmed.to_string()])
    };
    let ptypes = parse_spec_param_types(fallback_spec);
    let sel_block = DragWebBlockBean {
        id: rule_id.to_string(),
        op_code: target_op.to_string(),
        spec: fallback_spec.to_string(),
        spec2: String::new(),
        block_type: "c".to_string(),
        category: "css".to_string(),
        color: -15108462,
        code: fallback_code.to_string(),
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
    let catalog = load_css_json_catalog(blocks_json);

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
        let rule_id =
            process_css_rule_with_catalog(&mut ctx, &catalog, rule, is_root, root_idx, false);
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
