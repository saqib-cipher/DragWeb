use crate::models::{parse_hex_color_i32, parse_spec_param_types, DragWebBlockBean};
use rustc_hash::FxHashMap;
use serde_json::Value;

#[derive(Clone, Debug)]
struct JsJsonDef {
    id: String,
    category: String,
    block_type: String,
    color: i32,
    spec: String,
    spec2: String,
    code: String,
    segments: Vec<String>,
    param_indices: Vec<usize>,
    literal_score: usize,
}

fn parse_template_segments(raw_code: &str) -> (Vec<String>, Vec<usize>, usize) {
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
                if d_end + 1 < len && chars[d_end] == '$' && (chars[d_end + 1] == 's' || chars[d_end + 1] == 'd') {
                    let idx_num: usize = chars[i + 1..d_end].iter().collect::<String>().parse().unwrap_or(next_auto);
                    segments.push(curr_lit.clone());
                    curr_lit.clear();
                    indices.push(idx_num);
                    next_auto = idx_num + 1;
                    i = d_end + 2;
                    continue;
                }
            }
            if chars[i + 1] == 's' || chars[i + 1] == 'd' || chars[i + 1] == 'b' || chars[i + 1] == 'n' {
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

fn load_js_json_defs(blocks_json: &str) -> (Vec<JsJsonDef>, FxHashMap<String, JsJsonDef>) {
    let mut matchers = Vec::new();
    let mut by_id = FxHashMap::default();

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
            let is_js_relevant = cat_l.starts_with("js")
                || cat_l == "control"
                || cat_l == "operator"
                || cat_l == "comment"
                || cat_l == "asd"
                || cat_l == "widgets"
                || id.starts_with("js");
            if !is_js_relevant {
                continue;
            }

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
            let color_str = item.get("color").and_then(|v| v.as_str()).unwrap_or("#607D8B");
            let spec = item.get("spec").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let spec2 = item.get("spec2").and_then(|v| v.as_str()).unwrap_or("").to_string();
            let code = item
                .get("code")
                .or_else(|| item.get("template"))
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();

            let (segments, param_indices, literal_score) = parse_template_segments(&code);
            let def = JsJsonDef {
                id: id.clone(),
                category: cat,
                block_type: block_type.clone(),
                color: parse_hex_color_i32(color_str),
                spec,
                spec2,
                code: code.clone(),
                segments,
                param_indices,
                literal_score,
            };
            by_id.insert(id.clone(), def.clone());

            // Only build direct regex-free statement matcher for single-statement command blocks with non-empty static pattern
            let has_container_body = block_type == "c" || block_type == "e" || code.contains("{\n%");
            if !has_container_body
                && block_type != "b"
                && block_type != "d"
                && block_type != "s"
                && !code.trim().is_empty()
                && literal_score >= 3
            {
                matchers.push(def);
            }
        }
    }

    matchers.sort_by(|a, b| b.literal_score.cmp(&a.literal_score));
    (matchers, by_id)
}

fn match_flexible_prefix(input: &str, pos: usize, pattern: &str) -> Option<usize> {
    let in_bytes = input.as_bytes();
    let pat_bytes = pattern.as_bytes();
    let mut ii = pos;
    let mut pi = 0;

    while pi < pat_bytes.len() {
        let pc = pat_bytes[pi];
        if pc.is_ascii_whitespace() {
            while pi < pat_bytes.len() && pat_bytes[pi].is_ascii_whitespace() {
                pi += 1;
            }
            while ii < in_bytes.len() && in_bytes[ii].is_ascii_whitespace() {
                ii += 1;
            }
            continue;
        }
        if ii >= in_bytes.len() {
            return None;
        }
        let ic = in_bytes[ii];
        let matches = if (pc == b'"' || pc == b'\'') && (ic == b'"' || ic == b'\'') {
            true
        } else {
            pc == ic
        };
        if !matches {
            return None;
        }
        ii += 1;
        pi += 1;
    }
    Some(ii)
}

fn find_flexible(input: &str, start: usize, pattern: &str) -> Option<(usize, usize)> {
    if pattern.is_empty() {
        return Some((start, start));
    }
    for (idx, _) in input[start..].char_indices() {
        let abs_idx = start + idx;
        if let Some(end) = match_flexible_prefix(input, abs_idx, pattern) {
            return Some((abs_idx, end));
        }
    }
    None
}

fn match_statement_to_json_def(stmt: &str, def: &JsJsonDef) -> Option<Vec<String>> {
    if def.segments.is_empty() {
        return None;
    }
    let clean_stmt = stmt.trim().trim_end_matches(';').trim();
    if clean_stmt.is_empty() {
        return None;
    }

    let mut pos = match_flexible_prefix(clean_stmt, 0, &def.segments[0])?;
    if def.param_indices.is_empty() {
        if clean_stmt[pos..].trim().is_empty() {
            return Some(Vec::new());
        }
        return None;
    }

    let max_idx = *def.param_indices.iter().max().unwrap_or(&0);
    let mut params = vec![String::new(); max_idx];

    for k in 0..def.param_indices.len() {
        let next_seg = &def.segments[k + 1];
        let p_slot = def.param_indices[k].saturating_sub(1);

        if next_seg.is_empty() {
            if k + 1 == def.param_indices.len() {
                let raw_val = clean_stmt[pos..].trim();
                params[p_slot] = raw_val.to_string();
                pos = clean_stmt.len();
            } else {
                return None;
            }
        } else {
            let (found_start, found_end) = find_flexible(clean_stmt, pos, next_seg)?;
            let raw_val = clean_stmt[pos..found_start].trim();
            if raw_val.is_empty() && next_seg.len() > 1 {
                return None;
            }
            params[p_slot] = raw_val.to_string();
            pos = found_end;
        }
    }

    if !clean_stmt[pos..].trim().is_empty() {
        return None;
    }
    Some(params)
}

pub fn find_matching_pair(chars: &[char], start: usize, open: char, close: char) -> Option<usize> {
    let mut depth = 0;
    let mut in_str: Option<char> = None;
    let mut escape = false;

    for i in start..chars.len() {
        let c = chars[i];
        if escape {
            escape = false;
            continue;
        }
        if c == '\\' && in_str.is_some() {
            escape = true;
            continue;
        }
        if let Some(quote) = in_str {
            if c == quote {
                in_str = None;
            }
            continue;
        }
        if c == '"' || c == '\'' || c == '`' {
            in_str = Some(c);
            continue;
        }
        if c == open {
            depth += 1;
        } else if c == close {
            depth -= 1;
            if depth == 0 {
                return Some(i);
            }
        }
    }
    None
}

pub fn split_js_statements(code: &str) -> Vec<String> {
    let mut stmts = Vec::new();
    let mut current = String::new();
    let mut brace_depth = 0;
    let mut paren_depth = 0;
    let mut bracket_depth = 0;
    let mut in_str: Option<char> = None;
    let mut escape = false;

    let chars: Vec<char> = code.chars().collect();
    let len = chars.len();
    let mut i = 0;

    while i < len {
        let c = chars[i];

        if escape {
            current.push(c);
            escape = false;
            i += 1;
            continue;
        }

        if c == '\\' && in_str.is_some() {
            escape = true;
            current.push(c);
            i += 1;
            continue;
        }

        if let Some(quote) = in_str {
            if c == quote {
                in_str = None;
            }
            current.push(c);
            i += 1;
            continue;
        }

        if c == '"' || c == '\'' || c == '`' {
            in_str = Some(c);
            current.push(c);
            i += 1;
            continue;
        }

        if c == '/' && i + 1 < len && chars[i + 1] == '/' {
            let comment_start = i;
            while i < len && chars[i] != '\n' {
                i += 1;
            }
            if brace_depth == 0 && paren_depth == 0 && bracket_depth == 0 && current.trim().is_empty() {
                let c_line: String = chars[comment_start..i].iter().collect();
                let trimmed = c_line.trim();
                if !trimmed.is_empty() {
                    stmts.push(trimmed.to_string());
                }
            }
            continue;
        }

        if c == '/' && i + 1 < len && chars[i + 1] == '*' {
            let comment_start = i;
            i += 2;
            while i + 1 < len && !(chars[i] == '*' && chars[i + 1] == '/') {
                i += 1;
            }
            if i + 1 < len {
                i += 2;
            } else {
                i = len;
            }
            if brace_depth == 0 && paren_depth == 0 && bracket_depth == 0 && current.trim().is_empty() {
                let c_block: String = chars[comment_start..i].iter().collect();
                let trimmed = c_block.trim();
                if !trimmed.is_empty() {
                    stmts.push(trimmed.to_string());
                }
            }
            continue;
        }

        match c {
            '{' => {
                brace_depth += 1;
                current.push(c);
            }
            '}' => {
                if brace_depth > 0 {
                    brace_depth -= 1;
                }
                current.push(c);
                if brace_depth == 0 && paren_depth == 0 && bracket_depth == 0 {
                    let mut peek = i + 1;
                    while peek < len && chars[peek].is_whitespace() {
                        peek += 1;
                    }
                    let is_else = peek + 4 <= len && chars[peek..peek + 4].iter().collect::<String>() == "else";
                    let is_catch = peek + 5 <= len && chars[peek..peek + 5].iter().collect::<String>() == "catch";
                    let is_finally = peek + 7 <= len && chars[peek..peek + 7].iter().collect::<String>() == "finally";
                    if !is_else && !is_catch && !is_finally {
                        let trimmed = current.trim();
                        if !trimmed.is_empty() {
                            stmts.push(trimmed.to_string());
                            current.clear();
                        }
                    }
                }
            }
            '(' => {
                paren_depth += 1;
                current.push(c);
            }
            ')' => {
                if paren_depth > 0 {
                    paren_depth -= 1;
                }
                current.push(c);
            }
            '[' => {
                bracket_depth += 1;
                current.push(c);
            }
            ']' => {
                if bracket_depth > 0 {
                    bracket_depth -= 1;
                }
                current.push(c);
            }
            ';' => {
                if brace_depth == 0 && paren_depth == 0 && bracket_depth == 0 {
                    let trimmed = current.trim();
                    if !trimmed.is_empty() {
                        stmts.push(trimmed.to_string());
                        current.clear();
                    }
                } else {
                    current.push(c);
                }
            }
            '\n' => {
                if brace_depth == 0 && paren_depth == 0 && bracket_depth == 0 {
                    let trimmed = current.trim();
                    if !trimmed.is_empty() && (trimmed.ends_with('}') || trimmed.ends_with(')')) {
                        stmts.push(trimmed.to_string());
                        current.clear();
                    } else {
                        current.push(' ');
                    }
                } else {
                    current.push(c);
                }
            }
            _ => {
                current.push(c);
            }
        }
        i += 1;
    }

    let trimmed = current.trim();
    if !trimmed.is_empty() {
        stmts.push(trimmed.to_string());
    }

    stmts
}

pub fn extract_if_statement(s: &str) -> Option<(String, String, Option<String>)> {
    let chars: Vec<char> = s.chars().collect();
    let if_pos = s.find("if")?;
    let open_paren = chars.iter().enumerate().skip(if_pos + 2).find(|(_, &c)| c == '(').map(|(i, _)| i)?;
    let close_paren = find_matching_pair(&chars, open_paren, '(', ')')?;
    let cond: String = chars[open_paren + 1..close_paren].iter().collect();

    let open_brace = chars.iter().enumerate().skip(close_paren + 1).find(|(_, &c)| c == '{').map(|(i, _)| i)?;
    let close_brace = find_matching_pair(&chars, open_brace, '{', '}')?;
    let body: String = chars[open_brace + 1..close_brace].iter().collect();

    let remaining: String = chars[close_brace + 1..].iter().collect();
    let trimmed_rem = remaining.trim_start();
    if trimmed_rem.starts_with("else") {
        let else_chars: Vec<char> = remaining.chars().collect();
        if let Some(open_else_brace) = else_chars.iter().enumerate().find(|(_, &c)| c == '{').map(|(i, _)| i) {
            if let Some(close_else_brace) = find_matching_pair(&else_chars, open_else_brace, '{', '}') {
                let else_body: String = else_chars[open_else_brace + 1..close_else_brace].iter().collect();
                return Some((cond.trim().to_string(), body.trim().to_string(), Some(else_body.trim().to_string())));
            }
        }
    }

    Some((cond.trim().to_string(), body.trim().to_string(), None))
}

pub fn extract_loop_statement(s: &str, keyword: &str) -> Option<(String, String)> {
    let chars: Vec<char> = s.chars().collect();
    let key_pos = s.find(keyword)?;
    let open_paren = chars.iter().enumerate().skip(key_pos + keyword.len()).find(|(_, &c)| c == '(').map(|(i, _)| i)?;
    let close_paren = find_matching_pair(&chars, open_paren, '(', ')')?;
    let header: String = chars[open_paren + 1..close_paren].iter().collect();

    let open_brace = chars.iter().enumerate().skip(close_paren + 1).find(|(_, &c)| c == '{').map(|(i, _)| i)?;
    let close_brace = find_matching_pair(&chars, open_brace, '{', '}')?;
    let body: String = chars[open_brace + 1..close_brace].iter().collect();

    Some((header.trim().to_string(), body.trim().to_string()))
}

pub fn extract_for_limit(header: &str) -> String {
    if let Some(lt_pos) = header.find('<') {
        let after = &header[lt_pos + 1..];
        let after = after.trim_start_matches('=');
        if let Some(semi) = after.find(';') {
            return after[..semi].trim().to_string();
        }
        return after.trim().to_string();
    }
    header.to_string()
}

pub fn extract_event_listener(s: &str) -> Option<(String, String, String)> {
    let add_pos = s.find("addEventListener")?;
    let target = s[..add_pos].trim().trim_end_matches('.');
    let chars: Vec<char> = s.chars().collect();
    let open_paren = chars.iter().enumerate().skip(add_pos).find(|(_, &c)| c == '(').map(|(i, _)| i)?;
    let open_brace = chars.iter().enumerate().skip(open_paren).find(|(_, &c)| c == '{').map(|(i, _)| i)?;
    let close_brace = find_matching_pair(&chars, open_brace, '{', '}')?;

    let args_header: String = chars[open_paren + 1..open_brace].iter().collect();
    let event_name = if let Some(q1) = args_header.find(|c| c == '\'' || c == '"') {
        let quote_char = args_header.chars().nth(q1).unwrap_or('"');
        let rest = &args_header[q1 + 1..];
        if let Some(q2) = rest.find(quote_char) {
            &rest[..q2]
        } else {
            "click"
        }
    } else {
        "click"
    };

    let body: String = chars[open_brace + 1..close_brace].iter().collect();
    Some((target.to_string(), event_name.to_string(), body.trim().to_string()))
}

fn extract_set_timeout(s: &str) -> Option<(String, String)> {
    if !s.starts_with("setTimeout(") {
        return None;
    }
    let chars: Vec<char> = s.chars().collect();
    let open_paren = chars.iter().position(|&c| c == '(')?;
    let close_paren = find_matching_pair(&chars, open_paren, '(', ')')?;
    let open_brace = chars.iter().enumerate().skip(open_paren).find(|(_, &c)| c == '{').map(|(i, _)| i)?;
    let close_brace = find_matching_pair(&chars, open_brace, '{', '}')?;
    let body: String = chars[open_brace + 1..close_brace].iter().collect();
    let after: String = chars[close_brace + 1..close_paren].iter().collect();
    let ms = after.trim().trim_start_matches(',').trim();
    if ms.is_empty() {
        return None;
    }
    Some((ms.to_string(), body.trim().to_string()))
}

pub fn extract_var_declaration(s: &str) -> Option<(String, String)> {
    let trimmed = s.trim();
    let rest = if trimmed.starts_with("const ") {
        &trimmed[6..]
    } else if trimmed.starts_with("let ") {
        &trimmed[4..]
    } else if trimmed.starts_with("var ") {
        &trimmed[4..]
    } else {
        return None;
    };
    let eq_pos = rest.find('=')?;
    let name = rest[..eq_pos].trim();
    let expr = rest[eq_pos + 1..].trim();
    if name.is_empty() || name.contains(' ') || name.contains('(') {
        return None;
    }
    Some((name.to_string(), expr.to_string()))
}

pub fn extract_paren_arg(s: &str, prefix: &str) -> String {
    let p_pos = s.find(prefix).unwrap_or(0);
    let chars: Vec<char> = s.chars().collect();
    if let Some(open) = chars.iter().enumerate().skip(p_pos).find(|(_, &c)| c == '(').map(|(i, _)| i) {
        if let Some(close) = find_matching_pair(&chars, open, '(', ')') {
            let arg: String = chars[open + 1..close].iter().collect();
            return arg.trim().to_string();
        }
    }
    String::new()
}

pub fn extract_prop_assignment(s: &str, prop: &str) -> Option<(String, String)> {
    let prop_pos = s.find(prop)?;
    let target = s[..prop_pos].trim();
    let after_prop = &s[prop_pos + prop.len()..];
    let eq_pos = after_prop.find('=')?;
    let val = after_prop[eq_pos + 1..].trim().trim_end_matches(';').trim();
    Some((target.to_string(), val.to_string()))
}

pub fn extract_simple_assignment(s: &str) -> Option<(String, String)> {
    let eq_pos = s.find('=')?;
    let left = s[..eq_pos].trim();
    let right = s[eq_pos + 1..].trim().trim_end_matches(';').trim();
    if left.is_empty() || left.contains(' ') || left.contains('.') || left.contains('[') || left.contains('(') {
        return None;
    }
    Some((left.to_string(), right.to_string()))
}

fn apply_def_meta(bean: &mut DragWebBlockBean, by_id: &FxHashMap<String, JsJsonDef>, op_code: &str) {
    if let Some(def) = by_id.get(op_code) {
        bean.op_code = def.id.clone();
        bean.spec = def.spec.clone();
        bean.spec2 = def.spec2.clone();
        bean.block_type = def.block_type.clone();
        bean.category = def.category.clone();
        bean.color = def.color;
        bean.code = def.code.clone();
        bean.param_types = parse_spec_param_types(&def.spec);
        bean.tokenized_spec = bean.param_types.clone();
    }
}

fn process_js_nested_body_with_defs(
    body: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    matchers: &[JsJsonDef],
    by_id: &FxHashMap<String, JsJsonDef>,
) -> i32 {
    let stmts = split_js_statements(body);
    let mut first_id = -1;
    let mut prev_id = -1;
    for stmt in stmts {
        let id = process_js_statement_with_defs(&stmt, blocks, counter, false, -1, matchers, by_id);
        if id >= 0 {
            if first_id == -1 {
                first_id = id;
            }
            if prev_id != -1 {
                if let Some(prev) = blocks.iter_mut().find(|b| b.id == prev_id.to_string()) {
                    prev.next_block = id;
                }
            }
            prev_id = id;
        }
    }
    first_id
}

fn process_js_statement_with_defs(
    stmt: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    is_root: bool,
    stack_index: i32,
    matchers: &[JsJsonDef],
    by_id: &FxHashMap<String, JsJsonDef>,
) -> i32 {
    let s = stmt.trim();
    if s.is_empty() {
        return -1;
    }

    let block_id = *counter;
    *counter += 1;

    let mut bean = DragWebBlockBean {
        id: block_id.to_string(),
        op_code: String::new(),
        spec: String::new(),
        spec2: String::new(),
        block_type: " ".to_string(),
        category: "js".to_string(),
        color: -13158097,
        code: String::new(),
        parameters: Vec::new(),
        param_types: Vec::new(),
        sub_stack1: -1,
        sub_stack2: -1,
        next_block: -1,
        stack_index,
        is_connected_to_root: is_root,
        x: if is_root { 100 } else { 0 },
        y: if is_root { 100 } else { 0 },
        header_text: String::new(),
        tokenized_spec: Vec::new(),
    };

    // 0. Comments (// or /* ... */)
    if s.starts_with("//") || s.starts_with("/*") {
        let txt = if s.starts_with("//") {
            s.trim_start_matches("//").trim().to_string()
        } else {
            s.trim_start_matches("/*").trim_end_matches("*/").trim().to_string()
        };
        bean.op_code = "commentBlock".to_string();
        bean.spec = "// %s".to_string();
        bean.block_type = " ".to_string();
        bean.color = -11243910;
        bean.category = "comment".to_string();
        bean.parameters = vec![txt];
        bean.code = "// %1$s\n".to_string();
        apply_def_meta(&mut bean, by_id, "commentBlock");
        bean.param_types = parse_spec_param_types(&bean.spec);
        bean.tokenized_spec = bean.param_types.clone();
        blocks.push(bean);
        return block_id;
    }

    // 1. Check for if / if-else
    if s.starts_with("if") && s.contains('{') {
        if let Some((cond, body, else_body)) = extract_if_statement(s) {
            bean.color = -28416; // #FF9100 Control
            bean.category = "control".to_string();
            bean.parameters = vec![cond];
            bean.param_types = vec!["b".to_string()];
            if let Some(else_b) = else_body {
                bean.op_code = "jsIfElse".to_string();
                bean.spec = "if (%b) \n".to_string();
                bean.spec2 = "else \n".to_string();
                bean.block_type = "e".to_string();
                bean.code = "if (%1$s) {\n%2$s\n} else {\n%3$s\n}\n".to_string();
                apply_def_meta(&mut bean, by_id, "jsIfElse");
                bean.sub_stack1 = process_js_nested_body_with_defs(&body, blocks, counter, matchers, by_id);
                bean.sub_stack2 = process_js_nested_body_with_defs(&else_b, blocks, counter, matchers, by_id);
            } else {
                bean.op_code = "jsIf".to_string();
                bean.spec = "if (%b) {\n".to_string();
                bean.block_type = "c".to_string();
                bean.code = "if (%1$s) {\n%2$s\n}\n".to_string();
                apply_def_meta(&mut bean, by_id, "jsIf");
                bean.sub_stack1 = process_js_nested_body_with_defs(&body, blocks, counter, matchers, by_id);
            }
            bean.tokenized_spec = parse_spec_param_types(&bean.spec);
            blocks.push(bean);
            return block_id;
        }
    }

    // 2. Check for while (cond) { body }
    if s.starts_with("while") && s.contains('{') {
        if let Some((cond, body)) = extract_loop_statement(s, "while") {
            bean.op_code = "jsWhile".to_string();
            bean.spec = "while (%b) {\n".to_string();
            bean.block_type = "c".to_string();
            bean.color = -28416;
            bean.category = "control".to_string();
            bean.parameters = vec![cond];
            bean.code = "while (%1$s) {\n%2$s\n}\n".to_string();
            apply_def_meta(&mut bean, by_id, "jsWhile");
            bean.sub_stack1 = process_js_nested_body_with_defs(&body, blocks, counter, matchers, by_id);
            bean.param_types = parse_spec_param_types(&bean.spec);
            bean.tokenized_spec = bean.param_types.clone();
            blocks.push(bean);
            return block_id;
        }
    }

    // 3. Check for for (header) { body }
    if s.starts_with("for") && s.contains('{') {
        if let Some((header, body)) = extract_loop_statement(s, "for") {
            bean.op_code = "jsForLoop".to_string();
            bean.spec = "for (let i = 0; i < %d; i++) {\n".to_string();
            bean.block_type = "c".to_string();
            bean.color = -28416;
            bean.category = "control".to_string();
            let limit = extract_for_limit(&header);
            bean.parameters = vec![limit];
            bean.code = "for (let i = 0; i < %1$s; i++) {\n%2$s\n}\n".to_string();
            apply_def_meta(&mut bean, by_id, "jsForLoop");
            bean.sub_stack1 = process_js_nested_body_with_defs(&body, blocks, counter, matchers, by_id);
            bean.param_types = parse_spec_param_types(&bean.spec);
            bean.tokenized_spec = bean.param_types.clone();
            blocks.push(bean);
            return block_id;
        }
    }

    // 3b. setTimeout(function() { body }, ms)
    if let Some((ms, body)) = extract_set_timeout(s) {
        bean.op_code = "jsSetTimeout".to_string();
        bean.spec = "run after %d ms".to_string();
        bean.block_type = "c".to_string();
        bean.color = -1499549;
        bean.category = "js_timing".to_string();
        bean.parameters = vec![ms];
        bean.code = "setTimeout(function() {\n%2$s\n}, %1$s);\n".to_string();
        apply_def_meta(&mut bean, by_id, "jsSetTimeout");
        bean.sub_stack1 = process_js_nested_body_with_defs(&body, blocks, counter, matchers, by_id);
        bean.param_types = parse_spec_param_types(&bean.spec);
        bean.tokenized_spec = bean.param_types.clone();
        blocks.push(bean);
        return block_id;
    }

    // 4. Check for addEventListener
    if s.contains("addEventListener") && s.contains('{') {
        if let Some((target, event_name, body)) = extract_event_listener(s) {
            if event_name == "DOMContentLoaded" || (target == "document" && event_name.contains("load")) {
                bean.op_code = "jsOnLoad".to_string();
                bean.spec = "on DOMContentLoaded".to_string();
                bean.block_type = "c".to_string();
                bean.color = -16613443;
                bean.category = "js_events".to_string();
                bean.code = "document.addEventListener(\"DOMContentLoaded\", function() {\n%1$s\n});\n".to_string();
                apply_def_meta(&mut bean, by_id, "jsOnLoad");
                bean.sub_stack1 = process_js_nested_body_with_defs(&body, blocks, counter, matchers, by_id);
            } else {
                bean.op_code = "jsAddEventOnVar".to_string();
                bean.spec = "%var addEventListener %m.event".to_string();
                bean.block_type = "c".to_string();
                bean.color = -16613443;
                bean.category = "js_events".to_string();
                bean.parameters = vec![target, event_name];
                bean.code = "%1$s.addEventListener(\"%2$s\", (e) => {\n%3$s\n});\n".to_string();
                apply_def_meta(&mut bean, by_id, "jsAddEventOnVar");
                bean.sub_stack1 = process_js_nested_body_with_defs(&body, blocks, counter, matchers, by_id);
            }
            bean.param_types = parse_spec_param_types(&bean.spec);
            bean.tokenized_spec = bean.param_types.clone();
            blocks.push(bean);
            return block_id;
        }
    }

    // 5. Reference scan against blocks.json definitions (longest literal pattern match first)
    for def in matchers {
        if let Some(params) = match_statement_to_json_def(s, def) {
            let ptypes = parse_spec_param_types(&def.spec);
            if params.len() >= ptypes.len() {
                bean.op_code = def.id.clone();
                bean.spec = def.spec.clone();
                bean.spec2 = def.spec2.clone();
                bean.block_type = def.block_type.clone();
                bean.category = def.category.clone();
                bean.color = def.color;
                bean.code = def.code.clone();
                bean.parameters = params.into_iter().take(ptypes.len().max(def.param_indices.len())).collect();
                bean.param_types = ptypes.clone();
                bean.tokenized_spec = ptypes;
                blocks.push(bean);
                return block_id;
            }
        }
    }

    // 6. Variable declarations: (const|let|var) name = expr
    if let Some((vname, vexpr)) = extract_var_declaration(s) {
        bean.category = "variable".to_string();
        bean.color = -1683200; // #E65100
        bean.block_type = " ".to_string();
        let trimmed_expr = vexpr.trim().trim_end_matches(';');
        if (trimmed_expr.starts_with('"') && trimmed_expr.ends_with('"'))
            || (trimmed_expr.starts_with('\'') && trimmed_expr.ends_with('\''))
            || (trimmed_expr.starts_with('`') && trimmed_expr.ends_with('`'))
        {
            let inner_val = if trimmed_expr.len() >= 2 {
                &trimmed_expr[1..trimmed_expr.len() - 1]
            } else {
                ""
            };
            bean.op_code = "setVarString".to_string();
            bean.spec = "set %var to %s".to_string();
            bean.parameters = vec![vname, inner_val.to_string()];
            bean.param_types = vec!["v".to_string(), "s".to_string()];
            bean.code = "%1$s = \"%2$s\";\n".to_string();
        } else if trimmed_expr == "true" || trimmed_expr == "false" {
            bean.op_code = "setVarBoolean".to_string();
            bean.spec = "set %var to %b".to_string();
            bean.parameters = vec![vname, trimmed_expr.to_string()];
            bean.param_types = vec!["v".to_string(), "b".to_string()];
            bean.code = "%1$s = %2$s;\n".to_string();
        } else if trimmed_expr.parse::<f64>().is_ok() {
            bean.op_code = "setVarInt".to_string();
            bean.spec = "set %var to %d".to_string();
            bean.parameters = vec![vname, trimmed_expr.to_string()];
            bean.param_types = vec!["v".to_string(), "d".to_string()];
            bean.code = "%1$s = %2$s;\n".to_string();
        } else {
            bean.op_code = "jsVarLet".to_string();
            bean.spec = "let %s.inputOnly = %s.inputOnly".to_string();
            bean.parameters = vec![vname, trimmed_expr.to_string()];
            bean.code = "let %1$s = %2$s;\n".to_string();
            apply_def_meta(&mut bean, by_id, "jsVarLet");
            bean.param_types = parse_spec_param_types(&bean.spec);
        }
        bean.tokenized_spec = bean.param_types.clone();
        blocks.push(bean);
        return block_id;
    }

    // 7. Built-in fallbacks when blocks_json is empty (unit tests)
    if s.starts_with("console.log(") {
        let arg = extract_paren_arg(s, "console.log");
        bean.op_code = "jsConsoleLog".to_string();
        bean.spec = "console.log %s".to_string();
        bean.block_type = " ".to_string();
        bean.color = -1498717;
        bean.category = "js_dom".to_string();
        bean.parameters = vec![arg];
        bean.param_types = vec!["s".to_string()];
        bean.code = "console.log(%1$s);\n".to_string();
        apply_def_meta(&mut bean, by_id, "jsConsoleLog");
        bean.tokenized_spec = bean.param_types.clone();
        blocks.push(bean);
        return block_id;
    }

    if s.starts_with("alert(") {
        let arg = extract_paren_arg(s, "alert");
        bean.op_code = "jsAlert".to_string();
        bean.spec = "alert %s".to_string();
        bean.block_type = " ".to_string();
        bean.color = -1498717;
        bean.category = "js_dom".to_string();
        bean.parameters = vec![arg];
        bean.param_types = vec!["s".to_string()];
        bean.code = "alert(%1$s);\n".to_string();
        apply_def_meta(&mut bean, by_id, "jsAlert");
        bean.tokenized_spec = bean.param_types.clone();
        blocks.push(bean);
        return block_id;
    }

    if s.contains(".innerHTML") && s.contains('=') {
        if let Some((target, val)) = extract_prop_assignment(s, ".innerHTML") {
            bean.op_code = "jsSetInnerHTML".to_string();
            bean.spec = "set innerHTML of element %s.inputOnly to %s".to_string();
            bean.block_type = " ".to_string();
            bean.color = -1498717;
            bean.category = "js_dom".to_string();
            bean.parameters = vec![target, val];
            bean.param_types = vec!["s.inputOnly".to_string(), "s".to_string()];
            bean.code = "%1$s.innerHTML = %2$s;\n".to_string();
            bean.tokenized_spec = bean.param_types.clone();
            blocks.push(bean);
            return block_id;
        }
    }
    if s.contains(".textContent") && s.contains('=') {
        if let Some((target, val)) = extract_prop_assignment(s, ".textContent") {
            bean.op_code = "jsSetTextContent".to_string();
            bean.spec = "set textContent of element %s.inputOnly to %s".to_string();
            bean.block_type = " ".to_string();
            bean.color = -1498717;
            bean.category = "js_dom".to_string();
            bean.parameters = vec![target, val];
            bean.param_types = vec!["s.inputOnly".to_string(), "s".to_string()];
            bean.code = "%1$s.textContent = %2$s;\n".to_string();
            bean.tokenized_spec = bean.param_types.clone();
            blocks.push(bean);
            return block_id;
        }
    }

    // 8. Simple assignment: target = val
    if s.contains('=') && !s.contains("==") && !s.contains("=>") {
        if let Some((target, val)) = extract_simple_assignment(s) {
            bean.op_code = "setVarString".to_string();
            bean.spec = "set %var to %s".to_string();
            bean.block_type = " ".to_string();
            bean.color = -1683200;
            bean.category = "variable".to_string();
            bean.parameters = vec![target, val];
            bean.param_types = vec!["v".to_string(), "s".to_string()];
            bean.code = "%1$s = %2$s;\n".to_string();
            bean.tokenized_spec = bean.param_types.clone();
            blocks.push(bean);
            return block_id;
        }
    }

    // 9. Fallback: Add source directly (command block " ", never "s")
    bean.op_code = "asdN".to_string();
    bean.spec = "add source directly %s.inputOnly".to_string();
    bean.block_type = " ".to_string();
    bean.color = -12232092; // #455A64
    bean.category = "asd".to_string();
    let cleaned = s.trim_end_matches(';').trim().to_string();
    bean.parameters = vec![cleaned];
    bean.code = "%1$s;\n".to_string();
    if by_id.contains_key("asdN") {
        apply_def_meta(&mut bean, by_id, "asdN");
        bean.block_type = " ".to_string();
    } else if by_id.contains_key("addSourceCode") {
        apply_def_meta(&mut bean, by_id, "addSourceCode");
        bean.block_type = " ".to_string();
    }
    bean.param_types = parse_spec_param_types(&bean.spec);
    bean.tokenized_spec = bean.param_types.clone();
    blocks.push(bean);
    block_id
}

pub fn process_js_nested_body(body: &str, blocks: &mut Vec<DragWebBlockBean>, counter: &mut i32) -> i32 {
    let empty_by_id = FxHashMap::default();
    process_js_nested_body_with_defs(body, blocks, counter, &[], &empty_by_id)
}

pub fn process_js_statement(
    stmt: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    is_root: bool,
) -> i32 {
    let empty_by_id = FxHashMap::default();
    process_js_statement_with_defs(stmt, blocks, counter, is_root, if is_root { 0 } else { -1 }, &[], &empty_by_id)
}

pub fn generate_js_blocks(js_code: &str, blocks_json: &str) -> Vec<DragWebBlockBean> {
    let mut blocks = Vec::new();
    let mut counter = 10;
    let (matchers, by_id) = load_js_json_defs(blocks_json);
    let stmts = split_js_statements(js_code);

    let mut prev_block_id = -1;
    let mut root_idx = 0;
    for stmt in stmts.iter() {
        let is_root = prev_block_id == -1;
        let id = process_js_statement_with_defs(
            stmt,
            &mut blocks,
            &mut counter,
            is_root,
            root_idx,
            &matchers,
            &by_id,
        );
        if id >= 0 {
            if prev_block_id != -1 {
                if let Some(prev) = blocks.iter_mut().find(|b| b.id == prev_block_id.to_string()) {
                    prev.next_block = id;
                }
            }
            prev_block_id = id;
            root_idx += 1;
        }
    }

    blocks
}
