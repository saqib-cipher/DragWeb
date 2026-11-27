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
    body_param_indices: Vec<usize>,
}

struct JsJsonCatalog {
    container_matchers: Vec<JsJsonDef>,
    stmt_matchers: Vec<JsJsonDef>,
    expr_matchers: Vec<JsJsonDef>,
    by_id: FxHashMap<String, JsJsonDef>,
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
            if chars[i + 1] == '%' {
                curr_lit.push('%');
                i += 2;
                continue;
            }
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

/// Identifies which `%k$s` placeholders in a container `'c'` / `'e'` block represent nested
/// block bodies inside `{ ... }` (e.g., `if (%1$s) {\n%2$s\n}` -> `[2]`).
fn detect_body_param_indices(code: &str, segments: &[String], param_indices: &[usize]) -> Vec<usize> {
    let mut bodies = Vec::new();
    for k in 0..param_indices.len() {
        let before = segments[k].trim_end();
        let after = segments[k + 1].trim_start();
        if before.ends_with('{') && after.starts_with('}') {
            bodies.push(param_indices[k]);
        }
    }
    if bodies.is_empty() && (code.contains('{') && code.contains('}')) {
        let spec_count = parse_spec_param_types(code).len();
        if spec_count == 0 {
            if let Some(&last) = param_indices.last() {
                bodies.push(last);
            }
        }
    }
    bodies
}

fn load_js_json_catalog(blocks_json: &str) -> JsJsonCatalog {
    let mut container_matchers = Vec::new();
    let mut stmt_matchers = Vec::new();
    let mut expr_matchers = Vec::new();
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
            // Skip pure CSS / HTML-only / widget-CSS blocks
            if cat_l == "css"
                || cat_l.starts_with("css_")
                || cat_l == "csscontrol"
                || cat_l == "responsive"
                || cat_l == "html"
                || cat_l == "html_attr"
                || id == "commentBlockCss"
                || id == "commentBlockBold"
            {
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

            let (segments, param_indices, literal_score) = parse_template_segments(&code);
            let body_param_indices = if block_type == "c" || block_type == "e" {
                detect_body_param_indices(&code, &segments, &param_indices)
            } else {
                Vec::new()
            };

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
                body_param_indices,
            };
            by_id.insert(id.clone(), def.clone());

            if block_type == "c" || block_type == "e" {
                if !code.trim().is_empty() && literal_score >= 2 && !def.body_param_indices.is_empty() {
                    container_matchers.push(def);
                }
            } else if block_type == "b" || block_type == "d" || block_type == "s" {
                if !code.trim().is_empty()
                    && literal_score >= 1
                    && !id.starts_with("asd")
                    && id != "jsVarRef"
                    && id != "jsonObjectCreate"
                    && id != "jsTernary"
                {
                    expr_matchers.push(def);
                }
            } else if !code.trim().is_empty() && literal_score >= 3 && !id.starts_with("asd") {
                stmt_matchers.push(def);
            }
        }
    }

    container_matchers.sort_by(|a, b| {
        b.literal_score
            .cmp(&a.literal_score)
            .then_with(|| b.param_indices.len().cmp(&a.param_indices.len()))
    });
    stmt_matchers.sort_by(|a, b| {
        b.literal_score
            .cmp(&a.literal_score)
            .then_with(|| b.param_indices.len().cmp(&a.param_indices.len()))
    });
    expr_matchers.sort_by(|a, b| {
        let a_generic = i32::from(a.id == "jsonObjectGet" || a.id == "jsListGetItem");
        let b_generic = i32::from(b.id == "jsonObjectGet" || b.id == "jsListGetItem");
        a_generic
            .cmp(&b_generic)
            .then_with(|| b.literal_score.cmp(&a.literal_score))
            .then_with(|| b.param_indices.len().cmp(&a.param_indices.len()))
    });

    JsJsonCatalog {
        container_matchers,
        stmt_matchers,
        expr_matchers,
        by_id,
    }
}

fn is_valid_identifier(s: &str) -> bool {
    let t = s.trim();
    if t.is_empty() {
        return false;
    }
    let mut chars = t.chars();
    let first = chars.next().unwrap();
    if !first.is_ascii_alphabetic() && first != '_' && first != '$' {
        return false;
    }
    chars.all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '$')
}

fn is_keyword_token(s: &str) -> bool {
    matches!(
        s.trim(),
        "true" | "false" | "null" | "undefined" | "NaN" | "Infinity" | "this" | "arguments"
    )
}

/// Validates that a captured leading expression (`segments[0].is_empty()`) is actually an
/// expression/receiver rather than crossing statement boundaries like `function addTask() { ... }`.
fn is_valid_receiver_expr(raw: &str) -> bool {
    let t = raw.trim();
    if t.is_empty() {
        return false;
    }
    let prefixes = [
        "function ", "async function ", "if ", "if(", "for ", "for(", "while ", "while(",
        "try ", "try{", "const ", "let ", "var ", "return ", "class ", "switch ",
    ];
    if prefixes.iter().any(|p| t.starts_with(p)) {
        return false;
    }
    let mut brace_depth = 0i32;
    let mut paren_depth = 0i32;
    let mut bracket_depth = 0i32;
    let mut in_str: Option<char> = None;
    let mut escape = false;
    let chars: Vec<char> = t.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let ch = chars[i];
        if escape {
            escape = false;
            i += 1;
            continue;
        }
        if ch == '\\' && in_str.is_some() {
            escape = true;
            i += 1;
            continue;
        }
        if let Some(q) = in_str {
            if ch == q {
                in_str = None;
            }
            i += 1;
            continue;
        }
        if ch == '/' && i + 1 < chars.len() && chars[i + 1] == '/' {
            while i < chars.len() && chars[i] != '\n' {
                i += 1;
            }
            continue;
        }
        if ch == '/' && i + 1 < chars.len() && chars[i + 1] == '*' {
            i += 2;
            while i + 1 < chars.len() && !(chars[i] == '*' && chars[i + 1] == '/') {
                i += 1;
            }
            i = (i + 2).min(chars.len());
            continue;
        }
        match ch {
            '"' | '\'' | '`' => in_str = Some(ch),
            '{' => brace_depth += 1,
            '}' => brace_depth -= 1,
            '(' => paren_depth += 1,
            ')' => paren_depth -= 1,
            '[' => bracket_depth += 1,
            ']' => bracket_depth -= 1,
            ';' | '=' if brace_depth == 0 && paren_depth == 0 && bracket_depth == 0 => return false,
            _ => {}
        }
        if brace_depth < 0 || paren_depth < 0 || bracket_depth < 0 {
            return false;
        }
        i += 1;
    }
    brace_depth == 0 && paren_depth == 0 && bracket_depth == 0
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

/// Depth-, quote-, and comment-aware search for `pattern` at top delimiter level.
fn find_flexible_balanced(input: &str, start: usize, pattern: &str) -> Option<(usize, usize)> {
    if pattern.is_empty() {
        return Some((start, start));
    }
    let bytes = input.as_bytes();
    let mut i = start;
    let mut paren_depth = 0i32;
    let mut brace_depth = 0i32;
    let mut bracket_depth = 0i32;
    let mut in_str: Option<u8> = None;
    let mut escape = false;

    while i < bytes.len() {
        if in_str.is_none() && paren_depth == 0 && brace_depth == 0 && bracket_depth == 0 {
            if let Some(end) = match_flexible_prefix(input, i, pattern) {
                return Some((i, end));
            }
        }
        let b = bytes[i];
        if escape {
            escape = false;
            i += 1;
            continue;
        }
        if b == b'\\' && in_str.is_some() {
            escape = true;
            i += 1;
            continue;
        }
        if let Some(q) = in_str {
            if b == q {
                in_str = None;
            }
            i += 1;
            continue;
        }
        if b == b'/' && i + 1 < bytes.len() && bytes[i + 1] == b'/' {
            while i < bytes.len() && bytes[i] != b'\n' {
                i += 1;
            }
            continue;
        }
        if b == b'/' && i + 1 < bytes.len() && bytes[i + 1] == b'*' {
            i += 2;
            while i + 1 < bytes.len() && !(bytes[i] == b'*' && bytes[i + 1] == b'/') {
                i += 1;
            }
            i = (i + 2).min(bytes.len());
            continue;
        }
        match b {
            b'"' | b'\'' | b'`' => in_str = Some(b),
            b'(' => paren_depth += 1,
            b')' => {
                paren_depth -= 1;
                if paren_depth < 0 {
                    return None;
                }
            }
            b'{' => brace_depth += 1,
            b'}' => {
                brace_depth -= 1;
                if brace_depth < 0 {
                    return None;
                }
            }
            b'[' => bracket_depth += 1,
            b']' => {
                bracket_depth -= 1;
                if bracket_depth < 0 {
                    return None;
                }
            }
            _ => {}
        }
        let ch_len = input[i..].chars().next().map(|c| c.len_utf8()).unwrap_or(1);
        i += ch_len;
    }
    None
}

fn allow_empty_slot(prev_seg: &str, next_seg: &str) -> bool {
    let p = prev_seg.trim_end();
    let n = next_seg.trim_start();
    (p.ends_with('(') && n.starts_with(')')) || n.len() <= 1
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
        let prev_seg = &def.segments[k];
        let next_seg = &def.segments[k + 1];
        let p_slot = def.param_indices[k].saturating_sub(1);

        if next_seg.is_empty() {
            if k + 1 == def.param_indices.len() {
                let raw_val = clean_stmt[pos..].trim();
                if k == 0 && prev_seg.is_empty() && !is_valid_receiver_expr(raw_val) {
                    return None;
                }
                if def.id == "jsonObjectGet" && !is_valid_identifier(raw_val) {
                    return None;
                }
                params[p_slot] = raw_val.to_string();
                pos = clean_stmt.len();
            } else {
                return None;
            }
        } else {
            let (found_start, found_end) = find_flexible_balanced(clean_stmt, pos, next_seg)?;
            let raw_val = clean_stmt[pos..found_start].trim();
            if raw_val.is_empty() && !allow_empty_slot(prev_seg, next_seg) {
                return None;
            }
            if k == 0 && prev_seg.is_empty() && !is_valid_receiver_expr(raw_val) {
                return None;
            }
            if def.id == "jsonObjectGet" && (!is_valid_identifier(raw_val) && !is_valid_receiver_expr(raw_val)) {
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

fn find_matching_brace_bytes(s: &str, pos_after_open: usize) -> Option<usize> {
    let bytes = s.as_bytes();
    let mut depth = 1i32;
    let mut in_str: Option<u8> = None;
    let mut escape = false;
    let mut i = pos_after_open;

    while i < bytes.len() {
        let b = bytes[i];
        if escape {
            escape = false;
            i += 1;
            continue;
        }
        if b == b'\\' && in_str.is_some() {
            escape = true;
            i += 1;
            continue;
        }
        if let Some(q) = in_str {
            if b == q {
                in_str = None;
            }
            i += 1;
            continue;
        }
        if b == b'/' && i + 1 < bytes.len() && bytes[i + 1] == b'/' {
            while i < bytes.len() && bytes[i] != b'\n' {
                i += 1;
            }
            continue;
        }
        if b == b'/' && i + 1 < bytes.len() && bytes[i + 1] == b'*' {
            i += 2;
            while i + 1 < bytes.len() && !(bytes[i] == b'*' && bytes[i + 1] == b'/') {
                i += 1;
            }
            i = (i + 2).min(bytes.len());
            continue;
        }
        match b {
            b'"' | b'\'' | b'`' => in_str = Some(b),
            b'{' => depth += 1,
            b'}' => {
                depth -= 1;
                if depth == 0 {
                    return Some(i);
                }
            }
            _ => {}
        }
        let ch_len = s[i..].chars().next().map(|c| c.len_utf8()).unwrap_or(1);
        i += ch_len;
    }
    None
}

/// Extracts parameters + substack body strings for `'c'` / `'e'` container templates in `blocks.json`.
fn match_container_to_json_def(
    stmt: &str,
    def: &JsJsonDef,
) -> Option<(Vec<String>, Vec<String>)> {
    if def.segments.is_empty() || def.body_param_indices.is_empty() {
        return None;
    }
    let clean_stmt = stmt.trim().trim_end_matches(';').trim();
    if clean_stmt.is_empty() {
        return None;
    }

    let mut pos = match_flexible_prefix(clean_stmt, 0, &def.segments[0])?;
    let max_idx = *def.param_indices.iter().max().unwrap_or(&0);
    let mut all_slots = vec![String::new(); max_idx];

    for k in 0..def.param_indices.len() {
        let idx = def.param_indices[k];
        let prev_seg = &def.segments[k];
        let next_seg = &def.segments[k + 1];
        let is_body = def.body_param_indices.contains(&idx)
            && prev_seg.trim_end().ends_with('{')
            && next_seg.trim_start().starts_with('}');

        if is_body {
            let close_byte = find_matching_brace_bytes(clean_stmt, pos)?;
            let body_text = clean_stmt[pos..close_byte].trim().to_string();
            all_slots[idx.saturating_sub(1)] = body_text;

            let mut after_close_pos = close_byte + 1;
            while after_close_pos < clean_stmt.len()
                && clean_stmt.as_bytes()[after_close_pos].is_ascii_whitespace()
            {
                after_close_pos += 1;
            }
            let trimmed_next = next_seg.trim_start();
            let rest_of_next = trimmed_next.strip_prefix('}').unwrap_or(trimmed_next).trim();
            if rest_of_next.is_empty() {
                pos = after_close_pos;
            } else {
                pos = match_flexible_prefix(clean_stmt, after_close_pos, rest_of_next)?;
            }
        } else if next_seg.is_empty() {
            if k + 1 == def.param_indices.len() {
                all_slots[idx.saturating_sub(1)] = clean_stmt[pos..].trim().to_string();
                pos = clean_stmt.len();
            } else {
                return None;
            }
        } else {
            let (found_start, found_end) = find_flexible_balanced(clean_stmt, pos, next_seg)?;
            let raw_val = clean_stmt[pos..found_start].trim();
            if raw_val.is_empty() && !allow_empty_slot(prev_seg, next_seg) {
                return None;
            }
            if k == 0 && prev_seg.is_empty() && !is_valid_receiver_expr(raw_val) {
                return None;
            }
            all_slots[idx.saturating_sub(1)] = raw_val.to_string();
            pos = found_end;
        }
    }

    if !clean_stmt[pos..].trim().is_empty() {
        return None;
    }

    let mut header_params = Vec::new();
    let mut bodies = Vec::new();
    for slot_idx in 1..=max_idx {
        if def.body_param_indices.contains(&slot_idx) {
            bodies.push(all_slots[slot_idx - 1].clone());
        } else {
            header_params.push(all_slots[slot_idx - 1].clone());
        }
    }
    Some((header_params, bodies))
}

pub fn find_matching_pair(chars: &[char], start: usize, open: char, close: char) -> Option<usize> {
    let mut depth = 0;
    let mut in_str: Option<char> = None;
    let mut escape = false;
    let mut i = start;

    while i < chars.len() {
        let c = chars[i];
        if escape {
            escape = false;
            i += 1;
            continue;
        }
        if c == '\\' && in_str.is_some() {
            escape = true;
            i += 1;
            continue;
        }
        if let Some(quote) = in_str {
            if c == quote {
                in_str = None;
            }
            i += 1;
            continue;
        }
        if c == '/' && i + 1 < chars.len() && chars[i + 1] == '/' {
            while i < chars.len() && chars[i] != '\n' {
                i += 1;
            }
            continue;
        }
        if c == '/' && i + 1 < chars.len() && chars[i + 1] == '*' {
            i += 2;
            while i + 1 < chars.len() && !(chars[i] == '*' && chars[i + 1] == '/') {
                i += 1;
            }
            i = (i + 2).min(chars.len());
            continue;
        }
        if c == '"' || c == '\'' || c == '`' {
            in_str = Some(c);
            i += 1;
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
        i += 1;
    }
    None
}

fn next_non_ws_starts_continuation(chars: &[char], from: usize) -> bool {
    let len = chars.len();
    let mut peek = from;
    loop {
        while peek < len && chars[peek].is_whitespace() {
            peek += 1;
        }
        if peek + 1 < len && chars[peek] == '/' && chars[peek + 1] == '/' {
            while peek < len && chars[peek] != '\n' {
                peek += 1;
            }
            continue;
        }
        if peek + 1 < len && chars[peek] == '/' && chars[peek + 1] == '*' {
            peek += 2;
            while peek + 1 < len && !(chars[peek] == '*' && chars[peek + 1] == '/') {
                peek += 1;
            }
            peek = (peek + 2).min(len);
            continue;
        }
        break;
    }
    if peek >= len {
        return false;
    }
    if chars[peek] == '.' {
        return true;
    }
    let matches_kw = |kw: &str| -> bool {
        let k_chars: Vec<char> = kw.chars().collect();
        if peek + k_chars.len() > len {
            return false;
        }
        for j in 0..k_chars.len() {
            if chars[peek + j] != k_chars[j] {
                return false;
            }
        }
        if peek + k_chars.len() < len {
            let after = chars[peek + k_chars.len()];
            if after.is_alphanumeric() || after == '_' || after == '$' {
                return false;
            }
        }
        true
    };
    matches_kw("else") || matches_kw("catch") || matches_kw("finally")
}

fn prev_ends_with_binary_op(trimmed: &str) -> bool {
    let ops = [
        "+", "-", "*", "/", "%", "=", "&&", "||", "??", "==", "===", "!=", "!==", "<", ">", "<=",
        ">=", ",", ".", "?", ":", "=>",
    ];
    ops.iter().any(|op| trimmed.ends_with(op))
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
                    if !next_non_ws_starts_continuation(&chars, i + 1) {
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
                    let is_unfinished_header = (trimmed.starts_with("if")
                        || trimmed.starts_with("while")
                        || trimmed.starts_with("for")
                        || trimmed.starts_with("else")
                        || trimmed.starts_with("try")
                        || trimmed.starts_with("catch")
                        || trimmed.starts_with("function ")
                        || trimmed.starts_with("async function "))
                        && !trimmed.contains('{');
                    if !trimmed.is_empty()
                        && !is_unfinished_header
                        && !prev_ends_with_binary_op(trimmed)
                        && !next_non_ws_starts_continuation(&chars, i + 1)
                    {
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

/// Parses `function name(params) { body }`, `async function name(params) { body }`,
/// and `(const|let|var) name = [async] (params) => { body }`.
fn extract_function_definition(s: &str) -> Option<(&'static str, String, String, String)> {
    let trimmed = s.trim();
    if !trimmed.contains('{') {
        return None;
    }

    // 1. async function / function
    let (is_async, rest) = if let Some(r) = trimmed.strip_prefix("async ") {
        let rt = r.trim_start();
        if let Some(after_fn) = rt.strip_prefix("function") {
            if after_fn.starts_with(char::is_whitespace) {
                (true, after_fn.trim())
            } else {
                (false, "")
            }
        } else {
            (false, "")
        }
    } else if let Some(after_fn) = trimmed.strip_prefix("function") {
        if after_fn.starts_with(char::is_whitespace) {
            (false, after_fn.trim())
        } else {
            (false, "")
        }
    } else {
        (false, "")
    };

    if !rest.is_empty() {
        let chars: Vec<char> = rest.chars().collect();
        let open_paren = chars.iter().position(|&c| c == '(')?;
        let fname: String = chars[..open_paren].iter().collect();
        let fname = fname.trim().to_string();
        if !is_valid_identifier(&fname) {
            return None;
        }
        let close_paren = find_matching_pair(&chars, open_paren, '(', ')')?;
        let params: String = chars[open_paren + 1..close_paren].iter().collect();
        let open_brace = chars
            .iter()
            .enumerate()
            .skip(close_paren + 1)
            .find(|(_, &c)| c == '{')
            .map(|(i, _)| i)?;
        let between: String = chars[close_paren + 1..open_brace].iter().collect();
        if !between.trim().is_empty() {
            return None;
        }
        let close_brace = find_matching_pair(&chars, open_brace, '{', '}')?;
        let trailing: String = chars[close_brace + 1..].iter().collect();
        if !trailing.trim().trim_end_matches(';').trim().is_empty() {
            return None;
        }
        let body: String = chars[open_brace + 1..close_brace].iter().collect();
        let op = if is_async { "jsAsyncFunction" } else { "jsFunctionDefine" };
        return Some((op, fname, params.trim().to_string(), body.trim().to_string()));
    }

    // 2. const/let/var name = [async] (params) => { body }
    let after_kw = if let Some(r) = trimmed.strip_prefix("const ") {
        r
    } else if let Some(r) = trimmed.strip_prefix("let ") {
        r
    } else if let Some(r) = trimmed.strip_prefix("var ") {
        r
    } else {
        return None;
    };
    let eq_pos = after_kw.find('=')?;
    let name = after_kw[..eq_pos].trim();
    if !is_valid_identifier(name) {
        return None;
    }
    let rhs = after_kw[eq_pos + 1..].trim();
    let rhs_no_async = rhs.strip_prefix("async ").map(|r| r.trim()).unwrap_or(rhs);
    let chars: Vec<char> = rhs_no_async.chars().collect();
    if chars.is_empty() {
        return None;
    }

    let (params, after_params_idx) = if chars[0] == '(' {
        let close_p = find_matching_pair(&chars, 0, '(', ')')?;
        let p: String = chars[1..close_p].iter().collect();
        (p.trim().to_string(), close_p + 1)
    } else {
        let mut idx = 0;
        while idx < chars.len() && (chars[idx].is_ascii_alphanumeric() || chars[idx] == '_' || chars[idx] == '$') {
            idx += 1;
        }
        let p: String = chars[..idx].iter().collect();
        if !is_valid_identifier(&p) {
            return None;
        }
        (p, idx)
    };

    let after_p: String = chars[after_params_idx..].iter().collect();
    let after_p_trim = after_p.trim_start();
    let after_arrow = after_p_trim.strip_prefix("=>")?.trim_start();
    if !after_arrow.starts_with('{') {
        return None;
    }
    let arrow_chars: Vec<char> = after_arrow.chars().collect();
    let close_brace = find_matching_pair(&arrow_chars, 0, '{', '}')?;
    let trailing: String = arrow_chars[close_brace + 1..].iter().collect();
    if !trailing.trim().trim_end_matches(';').trim().is_empty() {
        return None;
    }
    let body: String = arrow_chars[1..close_brace].iter().collect();
    Some(("jsFunctionArrow", name.to_string(), params, body.trim().to_string()))
}

pub fn extract_if_statement(s: &str) -> Option<(String, String, Option<String>)> {
    let chars: Vec<char> = s.chars().collect();
    let trimmed = s.trim();
    if !trimmed.starts_with("if") {
        return None;
    }
    let after_if = trimmed[2..].trim_start();
    if !after_if.starts_with('(') {
        return None;
    }
    let open_paren = chars.iter().position(|&c| c == '(')?;
    let close_paren = find_matching_pair(&chars, open_paren, '(', ')')?;
    let cond: String = chars[open_paren + 1..close_paren].iter().collect();

    let after_cond: String = chars[close_paren + 1..].iter().collect();
    let after_cond_trim = after_cond.trim_start();

    if after_cond_trim.starts_with('{') {
        let open_brace = chars
            .iter()
            .enumerate()
            .skip(close_paren + 1)
            .find(|(_, &c)| c == '{')
            .map(|(i, _)| i)?;
        let close_brace = find_matching_pair(&chars, open_brace, '{', '}')?;
        let body: String = chars[open_brace + 1..close_brace].iter().collect();

        let rest: String = chars[close_brace + 1..].iter().collect();
        let rest_trim = rest.trim();
        let else_body = if rest_trim.starts_with("else") {
            let after_else = rest_trim[4..].trim();
            let after_chars: Vec<char> = after_else.chars().collect();
            if after_else.starts_with("if") {
                Some(after_else.to_string())
            } else if after_else.starts_with('{') {
                if let Some(eb_close) = find_matching_pair(&after_chars, 0, '{', '}') {
                    let eb: String = after_chars[1..eb_close].iter().collect();
                    Some(eb.trim().to_string())
                } else {
                    None
                }
            } else if !after_else.is_empty() {
                Some(after_else.trim_end_matches(';').trim().to_string())
            } else {
                None
            }
        } else {
            None
        };

        return Some((cond.trim().to_string(), body.trim().to_string(), else_body));
    }

    // Braceless single-statement `if (cond) stmt;` (and optional `else stmt;`)
    if after_cond_trim.is_empty() {
        return None;
    }
    let stmt_body = after_cond_trim.trim_end_matches(';').trim().to_string();
    Some((cond.trim().to_string(), stmt_body, None))
}

pub fn extract_loop_statement(s: &str, _keyword: &str) -> Option<(String, String)> {
    let chars: Vec<char> = s.chars().collect();
    let open_paren = chars.iter().position(|&c| c == '(')?;
    let close_paren = find_matching_pair(&chars, open_paren, '(', ')')?;
    let cond: String = chars[open_paren + 1..close_paren].iter().collect();

    let open_brace = chars
        .iter()
        .enumerate()
        .skip(close_paren + 1)
        .find(|(_, &c)| c == '{')
        .map(|(i, _)| i)?;
    let close_brace = find_matching_pair(&chars, open_brace, '{', '}')?;
    let body: String = chars[open_brace + 1..close_brace].iter().collect();

    Some((cond.trim().to_string(), body.trim().to_string()))
}

pub fn extract_for_limit(header: &str) -> String {
    if let Some(lt_pos) = header.find('<') {
        let after_lt = &header[lt_pos + 1..];
        let limit = after_lt.split(';').next().unwrap_or("10").trim();
        if !limit.is_empty() {
            return limit.to_string();
        }
    }
    "10".to_string()
}

fn split_top_level_args(args_str: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    let mut paren = 0i32;
    let mut brace = 0i32;
    let mut bracket = 0i32;
    let mut in_str: Option<char> = None;
    let mut esc = false;

    for ch in args_str.chars() {
        if esc {
            cur.push(ch);
            esc = false;
            continue;
        }
        if ch == '\\' && in_str.is_some() {
            cur.push(ch);
            esc = true;
            continue;
        }
        if let Some(q) = in_str {
            cur.push(ch);
            if ch == q {
                in_str = None;
            }
            continue;
        }
        match ch {
            '"' | '\'' | '`' => {
                in_str = Some(ch);
                cur.push(ch);
            }
            '(' => {
                paren += 1;
                cur.push(ch);
            }
            ')' => {
                paren -= 1;
                cur.push(ch);
            }
            '{' => {
                brace += 1;
                cur.push(ch);
            }
            '}' => {
                brace -= 1;
                cur.push(ch);
            }
            '[' => {
                bracket += 1;
                cur.push(ch);
            }
            ']' => {
                bracket -= 1;
                cur.push(ch);
            }
            ',' if paren == 0 && brace == 0 && bracket == 0 => {
                out.push(std::mem::take(&mut cur).trim().to_string());
            }
            _ => cur.push(ch),
        }
    }
    let last = cur.trim();
    if !last.is_empty() {
        out.push(last.to_string());
    }
    out
}

pub fn extract_event_listener(s: &str) -> Option<(String, String, String)> {
    extract_event_listener_ext(s).map(|(t, ev, body, _)| (t, ev, body))
}

fn extract_event_listener_ext(s: &str) -> Option<(String, String, String, bool)> {
    let trimmed = s.trim().trim_end_matches(';').trim();
    let add_pos = find_flexible_balanced(trimmed, 0, ".addEventListener")?.0;
    let target = trimmed[..add_pos].trim().to_string();
    if !is_valid_receiver_expr(&target) {
        return None;
    }
    let after_method = trimmed[add_pos + ".addEventListener".len()..].trim_start();
    if !after_method.starts_with('(') {
        return None;
    }
    let chars: Vec<char> = after_method.chars().collect();
    let close_paren = find_matching_pair(&chars, 0, '(', ')')?;
    let trailing: String = chars[close_paren + 1..].iter().collect();
    if !trailing.trim().is_empty() {
        return None;
    }
    let inner_args: String = chars[1..close_paren].iter().collect();
    let args = split_top_level_args(&inner_args);
    if args.len() != 2 {
        return None;
    }
    let event_name = unquote_js_string(&args[0])?;
    let cb = args[1].trim();
    let is_async = cb.starts_with("async ");
    let cb_body_src = cb.strip_prefix("async ").map(|r| r.trim()).unwrap_or(cb);
    if !cb_body_src.contains('{') {
        return None;
    }
    let cb_chars: Vec<char> = cb_body_src.chars().collect();
    let open_brace = cb_chars.iter().position(|&c| c == '{')?;
    let close_brace = find_matching_pair(&cb_chars, open_brace, '{', '}')?;
    let cb_trail: String = cb_chars[close_brace + 1..].iter().collect();
    if !cb_trail.trim().is_empty() {
        return None;
    }
    let body: String = cb_chars[open_brace + 1..close_brace].iter().collect();
    Some((target, event_name, body.trim().to_string(), is_async))
}

fn extract_event_callback(s: &str) -> Option<(&'static str, String, String, String)> {
    let trimmed = s.trim().trim_end_matches(';').trim();
    let (op, method_name) = if let Some((pos, _)) = find_flexible_balanced(trimmed, 0, ".addEventListener") {
        let _ = pos;
        ("jsAddEventCallback", ".addEventListener")
    } else if find_flexible_balanced(trimmed, 0, ".removeEventListener").is_some() {
        ("jsRemoveEventOnVar", ".removeEventListener")
    } else {
        return None;
    };
    let m_pos = find_flexible_balanced(trimmed, 0, method_name)?.0;
    let target = trimmed[..m_pos].trim().to_string();
    if !is_valid_receiver_expr(&target) {
        return None;
    }
    let after_m = trimmed[m_pos + method_name.len()..].trim_start();
    if !after_m.starts_with('(') {
        return None;
    }
    let chars: Vec<char> = after_m.chars().collect();
    let close_p = find_matching_pair(&chars, 0, '(', ')')?;
    let trailing: String = chars[close_p + 1..].iter().collect();
    if !trailing.trim().is_empty() {
        return None;
    }
    let inner: String = chars[1..close_p].iter().collect();
    let args = split_top_level_args(&inner);
    if args.len() != 2 {
        return None;
    }
    let event_name = unquote_js_string(&args[0])?;
    let callback_ref = args[1].trim().to_string();
    if callback_ref.contains('{') {
        return None;
    }
    Some((op, target, event_name, callback_ref))
}

fn extract_timer_statement(s: &str) -> Option<(&'static str, String, String, bool)> {
    let trimmed = s.trim().trim_end_matches(';').trim();
    let (fname, container_op, call_op) = if trimmed.starts_with("setTimeout") {
        ("setTimeout", "jsSetTimeout", "jsSetTimeoutCall")
    } else if trimmed.starts_with("setInterval") {
        ("setInterval", "jsSetInterval", "jsSetIntervalCall")
    } else {
        return None;
    };
    let after = trimmed[fname.len()..].trim_start();
    if !after.starts_with('(') {
        return None;
    }
    let chars: Vec<char> = after.chars().collect();
    let close_p = find_matching_pair(&chars, 0, '(', ')')?;
    let trail: String = chars[close_p + 1..].iter().collect();
    if !trail.trim().is_empty() {
        return None;
    }
    let inner: String = chars[1..close_p].iter().collect();
    let args = split_top_level_args(&inner);
    if args.len() != 2 {
        return None;
    }
    let cb = args[0].trim();
    let ms = args[1].trim().to_string();
    if cb.contains('{') {
        let cb_chars: Vec<char> = cb.chars().collect();
        let open_brace = cb_chars.iter().position(|&c| c == '{')?;
        let close_brace = find_matching_pair(&cb_chars, open_brace, '{', '}')?;
        let body: String = cb_chars[open_brace + 1..close_brace].iter().collect();
        Some((container_op, ms, body.trim().to_string(), true))
    } else {
        Some((call_op, ms, cb.to_string(), false))
    }
}

pub fn extract_var_declaration(s: &str) -> Option<(String, String)> {
    extract_var_declaration_kind(s).map(|(_, n, e)| (n, e))
}

fn extract_var_declaration_kind(s: &str) -> Option<(&'static str, String, String)> {
    let trimmed = s.trim().trim_end_matches(';').trim();
    let (kind, rest) = if let Some(r) = trimmed.strip_prefix("const ") {
        ("const", r)
    } else if let Some(r) = trimmed.strip_prefix("let ") {
        ("let", r)
    } else if let Some(r) = trimmed.strip_prefix("var ") {
        ("var", r)
    } else {
        return None;
    };
    let eq_pos = rest.find('=')?;
    let name = rest[..eq_pos].trim();
    let expr = rest[eq_pos + 1..].trim();
    if !is_valid_identifier(name) {
        return None;
    }
    Some((kind, name.to_string(), expr.to_string()))
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
    let prop_pos = find_flexible_balanced(s, 0, prop)?.0;
    let target = s[..prop_pos].trim();
    if !is_valid_receiver_expr(target) {
        return None;
    }
    let after_prop = &s[prop_pos + prop.len()..];
    let eq_pos = after_prop.find('=')?;
    let val = after_prop[eq_pos + 1..].trim().trim_end_matches(';').trim();
    Some((target.to_string(), val.to_string()))
}

pub fn extract_simple_assignment(s: &str) -> Option<(String, String)> {
    let trimmed = s.trim().trim_end_matches(';').trim();
    let eq_pos = find_flexible_balanced(trimmed, 0, "=")?.0;
    // Reject `==`, `===`, `!=`, `!==`, `<=`, `>=`, `=>`
    if eq_pos > 0 {
        let prev = trimmed.as_bytes()[eq_pos - 1];
        if matches!(prev, b'=' | b'!' | b'<' | b'>' | b'+' | b'-' | b'*' | b'/') {
            return None;
        }
    }
    if eq_pos + 1 < trimmed.len() {
        let next = trimmed.as_bytes()[eq_pos + 1];
        if next == b'=' || next == b'>' {
            return None;
        }
    }
    let left = trimmed[..eq_pos].trim();
    let right = trimmed[eq_pos + 1..].trim();
    if !is_valid_identifier(left) {
        return None;
    }
    Some((left.to_string(), right.to_string()))
}

fn strip_outer_matching_parens(s: &str) -> &str {
    let t = s.trim();
    if !t.starts_with('(') || !t.ends_with(')') {
        return t;
    }
    let chars: Vec<char> = t.chars().collect();
    if let Some(close) = find_matching_pair(&chars, 0, '(', ')') {
        if close == chars.len() - 1 {
            return strip_outer_matching_parens(&t[1..t.len() - 1]);
        }
    }
    t
}

fn is_plain_quoted_string(s: &str) -> bool {
    let t = s.trim();
    if t.len() < 2 {
        return false;
    }
    let q = t.chars().next().unwrap();
    if q != '"' && q != '\'' && q != '`' {
        return false;
    }
    if !t.ends_with(q) {
        return false;
    }
    let chars: Vec<char> = t.chars().collect();
    let mut esc = false;
    for (idx, &ch) in chars.iter().enumerate() {
        if idx == 0 {
            continue;
        }
        if esc {
            esc = false;
            continue;
        }
        if ch == '\\' {
            esc = true;
            continue;
        }
        if ch == q && idx < chars.len() - 1 {
            return false;
        }
    }
    true
}

fn unquote_js_string(s: &str) -> Option<String> {
    if !is_plain_quoted_string(s) {
        return None;
    }
    let t = s.trim();
    let q = t.chars().next()?;
    if q == '`' && t.contains("${") {
        return None;
    }
    let inner = &t[1..t.len() - 1];
    let mut out = String::with_capacity(inner.len());
    let mut chars = inner.chars();
    while let Some(ch) = chars.next() {
        if ch == '\\' {
            if let Some(next) = chars.next() {
                match next {
                    'n' => out.push('\n'),
                    't' => out.push('\t'),
                    'r' => out.push('\r'),
                    '\\' => out.push('\\'),
                    '"' => out.push('"'),
                    '\'' => out.push('\''),
                    other => out.push(other),
                }
            }
        } else {
            out.push(ch);
        }
    }
    Some(out)
}

fn parse_combined_spec_param_types(spec: &str, spec2: &str) -> Vec<String> {
    if spec2.trim().is_empty() {
        parse_spec_param_types(spec)
    } else {
        let combined = format!("{} {}", spec, spec2);
        parse_spec_param_types(&combined)
    }
}

fn make_bean_from_def(def: &JsJsonDef, id: String, params: Vec<String>) -> DragWebBlockBean {
    let ptypes = parse_spec_param_types(&def.spec);
    DragWebBlockBean {
        id,
        op_code: def.id.clone(),
        spec: def.spec.clone(),
        spec2: def.spec2.clone(),
        block_type: def.block_type.clone(),
        category: def.category.clone(),
        color: def.color,
        code: def.code.clone(),
        parameters: params,
        param_types: ptypes.clone(),
        sub_stack1: -1,
        sub_stack2: -1,
        next_block: -1,
        stack_index: -1,
        is_connected_to_root: false,
        x: 0,
        y: 0,
        header_text: String::new(),
        tokenized_spec: ptypes,
    }
}

fn build_var_ref_reporter(
    var_name: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    catalog: &JsJsonCatalog,
) -> String {
    let child_id = *counter;
    *counter += 1;
    let bean = if let Some(def) = catalog.by_id.get("jsVarRef") {
        make_bean_from_def(def, child_id.to_string(), vec![var_name.to_string()])
    } else {
        DragWebBlockBean {
            id: child_id.to_string(),
            op_code: "jsVarRef".to_string(),
            spec: "%var".to_string(),
            spec2: String::new(),
            block_type: "s".to_string(),
            category: "js_logic".to_string(),
            color: -16742021,
            code: "%1$s".to_string(),
            parameters: vec![var_name.to_string()],
            param_types: vec!["v".to_string()],
            sub_stack1: -1,
            sub_stack2: -1,
            next_block: -1,
            stack_index: -1,
            is_connected_to_root: false,
            x: 0,
            y: 0,
            header_text: String::new(),
            tokenized_spec: vec!["v".to_string()],
        }
    };
    blocks.push(bean);
    format!("@{}", child_id)
}

/// Searches for a binary operator at top parenthesis/bracket/brace/string depth, returning the
/// **rightmost** match so left-associative expressions parse cleanly, skipping unary prefix `+`/`-`.
fn find_top_level_binop<'a>(expr: &str, ops: &[(&str, &'a str)]) -> Option<(usize, usize, &'a str)> {
    let bytes = expr.as_bytes();
    let mut paren = 0i32;
    let mut bracket = 0i32;
    let mut brace = 0i32;
    let mut in_str: Option<u8> = None;
    let mut esc = false;
    let mut found: Option<(usize, usize, &'a str)> = None;
    let mut i = 0;

    while i < bytes.len() {
        let b = bytes[i];
        if esc {
            esc = false;
            i += 1;
            continue;
        }
        if b == b'\\' && in_str.is_some() {
            esc = true;
            i += 1;
            continue;
        }
        if let Some(q) = in_str {
            if b == q {
                in_str = None;
            }
            i += 1;
            continue;
        }
        match b {
            b'"' | b'\'' | b'`' => {
                in_str = Some(b);
                i += 1;
                continue;
            }
            b'(' => {
                paren += 1;
                i += 1;
                continue;
            }
            b')' => {
                paren -= 1;
                i += 1;
                continue;
            }
            b'[' => {
                bracket += 1;
                i += 1;
                continue;
            }
            b']' => {
                bracket -= 1;
                i += 1;
                continue;
            }
            b'{' => {
                brace += 1;
                i += 1;
                continue;
            }
            b'}' => {
                brace -= 1;
                i += 1;
                continue;
            }
            _ => {}
        }
        if paren == 0 && bracket == 0 && brace == 0 {
            for &(op_str, op_id) in ops {
                let op_bytes = op_str.as_bytes();
                if i + op_bytes.len() <= bytes.len() && &bytes[i..i + op_bytes.len()] == op_bytes {
                    // Prevent `<` / `>` / `=` from matching inside `<=`, `>=`, `===`, `!==`, `=>`, `++`, `--`
                    let prev = if i > 0 { Some(bytes[i - 1]) } else { None };
                    let next = if i + op_bytes.len() < bytes.len() {
                        Some(bytes[i + op_bytes.len()])
                    } else {
                        None
                    };
                    if (op_str == "<" || op_str == ">") && (next == Some(b'=') || prev == Some(b'=') || next == Some(b'<') || next == Some(b'>')) {
                        continue;
                    }
                    if op_str == "==" && (prev == Some(b'=') || prev == Some(b'!') || next == Some(b'=')) {
                        continue;
                    }
                    if op_str == "+" || op_str == "-" {
                        if prev == Some(op_bytes[0]) || next == Some(op_bytes[0]) {
                            continue;
                        }
                        // Check unary leading or following an operator
                        let before = expr[..i].trim_end();
                        if before.is_empty() || before.ends_with(|c: char| "+-*/%=<>!&|?:(".contains(c)) {
                            continue;
                        }
                    }
                    found = Some((i, i + op_bytes.len(), op_id));
                    break;
                }
            }
        }
        let ch_len = expr[i..].chars().next().map(|c| c.len_utf8()).unwrap_or(1);
        i += ch_len;
    }
    found
}

fn find_top_level_ternary(expr: &str) -> Option<(String, String, String)> {
    let bytes = expr.as_bytes();
    let mut paren = 0i32;
    let mut bracket = 0i32;
    let mut brace = 0i32;
    let mut in_str: Option<u8> = None;
    let mut esc = false;
    let mut q_pos: Option<usize> = None;
    let mut nested_ternary = 0i32;
    let mut i = 0;

    while i < bytes.len() {
        let b = bytes[i];
        if esc {
            esc = false;
            i += 1;
            continue;
        }
        if b == b'\\' && in_str.is_some() {
            esc = true;
            i += 1;
            continue;
        }
        if let Some(q) = in_str {
            if b == q {
                in_str = None;
            }
            i += 1;
            continue;
        }
        match b {
            b'"' | b'\'' | b'`' => in_str = Some(b),
            b'(' => paren += 1,
            b')' => paren -= 1,
            b'[' => bracket += 1,
            b']' => bracket -= 1,
            b'{' => brace += 1,
            b'}' => brace -= 1,
            b'?' if paren == 0 && bracket == 0 && brace == 0 => {
                // Skip `?.` and `??`
                let next = bytes.get(i + 1).copied();
                if next == Some(b'.') || next == Some(b'?') {
                    i += 2;
                    continue;
                }
                if q_pos.is_none() {
                    q_pos = Some(i);
                } else {
                    nested_ternary += 1;
                }
            }
            b':' if paren == 0 && bracket == 0 && brace == 0 && q_pos.is_some() => {
                if nested_ternary == 0 {
                    let qp = q_pos.unwrap();
                    return Some((
                        expr[..qp].trim().to_string(),
                        expr[qp + 1..i].trim().to_string(),
                        expr[i + 1..].trim().to_string(),
                    ));
                } else {
                    nested_ternary -= 1;
                }
            }
            _ => {}
        }
        let ch_len = expr[i..].chars().next().map(|c| c.len_utf8()).unwrap_or(1);
        i += ch_len;
    }
    None
}

fn try_build_operator_expr(
    unwrapped: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    catalog: &JsJsonCatalog,
    depth: usize,
) -> Option<String> {
    let levels: &[&[(&str, &str)]] = &[
        &[("||", "opOr")],
        &[("&&", "opAnd")],
        &[("??", "opNullish")],
        &[("===", "opEqual"), ("!==", "opNotEqual"), ("==", "opLooseEq")],
        &[("<=", "opLessEq"), (">=", "opGreaterEq"), ("<", "opLess"), (">", "opGreater")],
        &[("+", "opAdd"), ("-", "opSub")],
        &[("*", "opMul"), ("/", "opDiv"), ("%", "opMod")],
    ];

    for &level in levels {
        if let Some((start, end, op_id)) = find_top_level_binop(unwrapped, level) {
            if let Some(def) = catalog.by_id.get(op_id) {
                let lhs_raw = unwrapped[..start].trim();
                let rhs_raw = unwrapped[end..].trim();
                if !lhs_raw.is_empty() && !rhs_raw.is_empty() {
                    let ptypes = parse_spec_param_types(&def.spec);
                    let lhs = resolve_js_param_value(lhs_raw, ptypes.first().map(|s| s.as_str()).unwrap_or("s.inputOnly"), blocks, counter, catalog, depth + 1);
                    let rhs = resolve_js_param_value(rhs_raw, ptypes.get(1).map(|s| s.as_str()).unwrap_or("s.inputOnly"), blocks, counter, catalog, depth + 1);
                    let child_id = *counter;
                    *counter += 1;
                    blocks.push(make_bean_from_def(def, child_id.to_string(), vec![lhs, rhs]));
                    return Some(format!("@{}", child_id));
                }
            }
        }
    }

    if let Some((cond_s, then_s, else_s)) = find_top_level_ternary(unwrapped) {
        if let Some(def) = catalog.by_id.get("jsTernary") {
            let c = resolve_js_param_value(&cond_s, "s.inputOnly", blocks, counter, catalog, depth + 1);
            let t = resolve_js_param_value(&then_s, "s.inputOnly", blocks, counter, catalog, depth + 1);
            let e = resolve_js_param_value(&else_s, "s.inputOnly", blocks, counter, catalog, depth + 1);
            let child_id = *counter;
            *counter += 1;
            blocks.push(make_bean_from_def(def, child_id.to_string(), vec![c, t, e]));
            return Some(format!("@{}", child_id));
        }
    }

    if let Some(rest) = unwrapped.strip_prefix('!') {
        if !rest.starts_with('=') {
            if let Some(def) = catalog.by_id.get("opNot") {
                let operand = resolve_js_param_value(rest.trim(), "b", blocks, counter, catalog, depth + 1);
                let child_id = *counter;
                *counter += 1;
                blocks.push(make_bean_from_def(def, child_id.to_string(), vec![operand]));
                return Some(format!("@{}", child_id));
            }
        }
    }

    None
}

/// Resolves an expression or parameter according to its `BlockDef` chip type (`s`, `s.inputOnly`,
/// `d`, `b`, `v`, `%selector`, `%m.*`) so `BlockCodeCompiler` emits valid non-double-quoted JS.
fn resolve_js_param_value(
    raw: &str,
    ptype: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    catalog: &JsJsonCatalog,
    depth: usize,
) -> String {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return String::new();
    }

    // String literals: strip outer JS quotes for auto-quoted `%s`, selector, and `%m.*` chips!
    if let Some(unquoted) = unquote_js_string(trimmed) {
        if ptype == "s.inputOnly" {
            // `s.inputOnly` emits verbatim (keep quotes so `const x = "hi"` or `fetch("/api")` is valid JS)
            return trimmed.to_string();
        }
        return unquoted;
    }

    if trimmed == "true" || trimmed == "false" || trimmed.parse::<f64>().is_ok() {
        return trimmed.to_string();
    }

    if depth > 6 {
        return trimmed.to_string();
    }

    let unwrapped = strip_outer_matching_parens(trimmed);

    // Bare identifier / variable name
    if is_valid_identifier(unwrapped) {
        if ptype == "s" && !is_keyword_token(unwrapped) {
            // Auto-quoted `%s` chip MUST box variable identifier into `@jsVarRef` so it isn't compiled as string literal `"x"`!
            return build_var_ref_reporter(unwrapped, blocks, counter, catalog);
        }
        return unwrapped.to_string();
    }

    // Binary / unary / ternary operators respecting JS operator precedence
    if let Some(op_ref) = try_build_operator_expr(unwrapped, blocks, counter, catalog, depth) {
        return op_ref;
    }

    // Match against expression / DOM / string / Math reporter blocks loaded from blocks.json
    for def in &catalog.expr_matchers {
        if let Some(child_params) = match_statement_to_json_def(unwrapped, def) {
            let child_id = *counter;
            *counter += 1;
            let cptypes = parse_spec_param_types(&def.spec);
            let mut resolved_child_params = Vec::with_capacity(child_params.len());
            for (ci, cv) in child_params.into_iter().enumerate() {
                let cpt = cptypes.get(ci).map(|s| s.as_str()).unwrap_or("s");
                resolved_child_params.push(resolve_js_param_value(&cv, cpt, blocks, counter, catalog, depth + 1));
            }
            let bean = make_bean_from_def(
                def,
                child_id.to_string(),
                resolved_child_params.into_iter().take(cptypes.len().max(def.param_indices.len())).collect(),
            );
            blocks.push(bean);
            return format!("@{}", child_id);
        }
    }

    if ptype == "b" {
        if let Some(def) = catalog.by_id.get("asdB") {
            let child_id = *counter;
            *counter += 1;
            blocks.push(make_bean_from_def(def, child_id.to_string(), vec![trimmed.to_string()]));
            return format!("@{}", child_id);
        }
    }

    // If a plain `%s` or `%var` chip receives an unmatched JS expression (e.g. `arr[i].name`),
    // wrap in `asdS` so `BlockCodeCompiler` emits the expression verbatim rather than stringifying it.
    if ptype == "s" || ptype == "v" {
        if let Some(def) = catalog.by_id.get("asdS") {
            let child_id = *counter;
            *counter += 1;
            blocks.push(make_bean_from_def(def, child_id.to_string(), vec![trimmed.to_string()]));
            return format!("@{}", child_id);
        }
    }

    trimmed.to_string()
}

fn resolve_bean_params(
    raw_params: Vec<String>,
    ptypes: &[String],
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    catalog: &JsJsonCatalog,
) -> Vec<String> {
    raw_params
        .into_iter()
        .enumerate()
        .map(|(i, p)| {
            let pt = ptypes.get(i).map(|s| s.as_str()).unwrap_or("s");
            if pt == "v" {
                let t = p.trim();
                if is_valid_identifier(t) {
                    t.to_string()
                } else {
                    resolve_js_param_value(t, pt, blocks, counter, catalog, 0)
                }
            } else if pt == "m.list" {
                p.trim().to_string()
            } else if pt == "m.event" || pt == "id.selector" || pt == "selector" || pt == "c.selector" || pt.starts_with("m.") {
                unquote_js_string(&p).unwrap_or_else(|| p.trim().to_string())
            } else {
                resolve_js_param_value(&p, pt, blocks, counter, catalog, 0)
            }
        })
        .collect()
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

fn process_js_nested_body_with_catalog(
    body: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    catalog: &JsJsonCatalog,
) -> i32 {
    let stmts = split_js_statements(body);
    let mut first_id = -1;
    let mut prev_id = -1;
    for stmt in stmts {
        let id = process_js_statement_with_catalog(&stmt, blocks, counter, false, -1, catalog);
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

fn process_js_statement_with_catalog(
    stmt: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    is_root: bool,
    stack_index: i32,
    catalog: &JsJsonCatalog,
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
        category: "js_logic".to_string(),
        color: -16742021,
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
        bean.spec = "// %s.inputOnly".to_string();
        bean.block_type = " ".to_string();
        bean.color = -11243910;
        bean.category = "comment".to_string();
        bean.parameters = vec![txt];
        bean.code = "// %1$s\n".to_string();
        apply_def_meta(&mut bean, &catalog.by_id, "commentBlock");
        bean.param_types = parse_spec_param_types(&bean.spec);
        bean.tokenized_spec = bean.param_types.clone();
        blocks.push(bean);
        return block_id;
    }

    // 1. Structured Function & Arrow Function Definitions BEFORE generic container matching
    if let Some((fn_op, fname, fparams, fbody)) = extract_function_definition(s) {
        bean.op_code = fn_op.to_string();
        bean.block_type = "c".to_string();
        bean.category = "control".to_string();
        bean.color = -1683200;
        bean.spec = match fn_op {
            "jsAsyncFunction" => "async function %s.inputOnly ( %s.inputOnly )".to_string(),
            "jsFunctionArrow" => "const %s.inputOnly = ( %s.inputOnly ) =>".to_string(),
            _ => "function %s.inputOnly ( %s.inputOnly )".to_string(),
        };
        bean.code = match fn_op {
            "jsAsyncFunction" => "async function %1$s(%2$s) {\n%3$s\n}\n".to_string(),
            "jsFunctionArrow" => "const %1$s = (%2$s) => {\n%3$s\n};\n".to_string(),
            _ => "function %1$s(%2$s) {\n%3$s\n}\n".to_string(),
        };
        apply_def_meta(&mut bean, &catalog.by_id, fn_op);
        bean.parameters = vec![fname, fparams];
        bean.param_types = parse_spec_param_types(&bean.spec);
        bean.tokenized_spec = bean.param_types.clone();
        bean.sub_stack1 = process_js_nested_body_with_catalog(&fbody, blocks, counter, catalog);
        blocks.push(bean);
        return block_id;
    }

    // 2. Control Containers (`if/else`, `while`, `for`, `setTimeout/setInterval` container, inline `addEventListener`)
    if let Some((cond, body, else_body)) = extract_if_statement(s) {
        bean.color = -689152;
        bean.category = "control".to_string();
        if let Some(else_b) = else_body {
            bean.op_code = "jsIfElse".to_string();
            bean.spec = "if ( %b )".to_string();
            bean.spec2 = "else".to_string();
            bean.block_type = "e".to_string();
            bean.code = "if (%1$s) {\n%2$s\n} else {\n%3$s\n}\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsIfElse");
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![cond], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
            bean.sub_stack2 = process_js_nested_body_with_catalog(&else_b, blocks, counter, catalog);
        } else {
            bean.op_code = "jsIf".to_string();
            bean.spec = "if ( %b )".to_string();
            bean.block_type = "c".to_string();
            bean.code = "if (%1$s) {\n%2$s\n}\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsIf");
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![cond], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
        }
        blocks.push(bean);
        return block_id;
    }

    if s.starts_with("while") && s.contains('{') {
        if let Some((cond, body)) = extract_loop_statement(s, "while") {
            bean.op_code = "jsWhile".to_string();
            bean.spec = "while ( %b )".to_string();
            bean.block_type = "c".to_string();
            bean.color = -689152;
            bean.category = "control".to_string();
            bean.code = "while (%1$s) {\n%2$s\n}\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsWhile");
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![cond], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
            blocks.push(bean);
            return block_id;
        }
    }

    if s.starts_with("for") && s.contains('{') {
        if let Some((header, body)) = extract_loop_statement(s, "for") {
            if header.contains(" of ") && catalog.by_id.contains_key("jsForOf") {
                if let Some((left, right)) = header.split_once(" of ") {
                    let var_name = left
                        .trim()
                        .trim_start_matches("const ")
                        .trim_start_matches("let ")
                        .trim_start_matches("var ")
                        .trim()
                        .to_string();
                    bean.op_code = "jsForOf".to_string();
                    apply_def_meta(&mut bean, &catalog.by_id, "jsForOf");
                    let ptypes = parse_spec_param_types(&bean.spec);
                    bean.parameters = resolve_bean_params(
                        vec![var_name, right.trim().to_string()],
                        &ptypes,
                        blocks,
                        counter,
                        catalog,
                    );
                    bean.param_types = ptypes.clone();
                    bean.tokenized_spec = ptypes;
                    bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
                    blocks.push(bean);
                    return block_id;
                }
            }
            bean.op_code = "jsForLoop".to_string();
            bean.spec = "for i < %d".to_string();
            bean.block_type = "c".to_string();
            bean.color = -689152;
            bean.category = "control".to_string();
            let limit = extract_for_limit(&header);
            bean.code = "for (let i = 0; i < %1$s; i++) {\n%2$s\n}\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsForLoop");
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![limit], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
            blocks.push(bean);
            return block_id;
        }
    }

    if let Some((timer_op, ms, arg_or_body, is_container)) = extract_timer_statement(s) {
        bean.op_code = timer_op.to_string();
        bean.category = "js_timing".to_string();
        bean.color = -16750244;
        if is_container {
            bean.block_type = "c".to_string();
            bean.spec = if timer_op == "jsSetInterval" { "every %d ms run" } else { "after %d ms run" }.to_string();
            bean.code = if timer_op == "jsSetInterval" {
                "setInterval(() => {\n%2$s\n}, %1$s);\n"
            } else {
                "setTimeout(() => {\n%2$s\n}, %1$s);\n"
            }.to_string();
            apply_def_meta(&mut bean, &catalog.by_id, timer_op);
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![ms], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            bean.sub_stack1 = process_js_nested_body_with_catalog(&arg_or_body, blocks, counter, catalog);
        } else {
            bean.block_type = " ".to_string();
            bean.spec = if timer_op == "jsSetIntervalCall" {
                "every %d ms call %s.inputOnly"
            } else {
                "after %d ms call %s.inputOnly"
            }.to_string();
            bean.code = if timer_op == "jsSetIntervalCall" {
                "setInterval(%2$s, %1$s);\n"
            } else {
                "setTimeout(%2$s, %1$s);\n"
            }.to_string();
            apply_def_meta(&mut bean, &catalog.by_id, timer_op);
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![ms, arg_or_body], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
        }
        blocks.push(bean);
        return block_id;
    }

    if let Some((target, event_name, body, is_async)) = extract_event_listener_ext(s) {
        if event_name == "DOMContentLoaded" && (target == "document" || target == "window") {
            bean.op_code = "jsOnLoad".to_string();
            bean.spec = "on DOMContentLoaded".to_string();
            bean.block_type = "c".to_string();
            bean.color = -16615491;
            bean.category = "js_events".to_string();
            bean.code = "document.addEventListener(\"DOMContentLoaded\", () => {\n%1$s\n});\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsOnLoad");
            bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
        } else if event_name == "scroll" && target == "window" {
            bean.op_code = "jsOnScroll".to_string();
            bean.spec = "window on scroll".to_string();
            bean.block_type = "c".to_string();
            bean.color = -16615491;
            bean.category = "js_events".to_string();
            bean.code = "window.addEventListener(\"scroll\", () => {\n%1$s\n});\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsOnScroll");
            bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
        } else {
            let op = if is_async { "jsAddEventAsyncOnVar" } else { "jsAddEventOnVar" };
            bean.op_code = op.to_string();
            bean.spec = if is_async {
                "%var .addEventListener( %m.event , async )"
            } else {
                "%var .addEventListener( %m.event )"
            }.to_string();
            bean.block_type = "c".to_string();
            bean.color = -16615491;
            bean.category = "js_events".to_string();
            bean.code = if is_async {
                "%1$s.addEventListener(\"%2$s\", async (e) => {\n%3$s\n});\n"
            } else {
                "%1$s.addEventListener(\"%2$s\", (e) => {\n%3$s\n});\n"
            }.to_string();
            apply_def_meta(&mut bean, &catalog.by_id, op);
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![target, event_name], &ptypes, blocks, counter, catalog);
            bean.sub_stack1 = process_js_nested_body_with_catalog(&body, blocks, counter, catalog);
        }
        bean.param_types = parse_spec_param_types(&bean.spec);
        bean.tokenized_spec = bean.param_types.clone();
        blocks.push(bean);
        return block_id;
    }

    // 3. Generic scan against ALL container ('c' / 'e') blocks in blocks.json (try/catch, forEach, fetch, custom blocks)
    for def in &catalog.container_matchers {
        if let Some((header_params, bodies)) = match_container_to_json_def(s, def) {
            bean.op_code = def.id.clone();
            bean.spec = def.spec.clone();
            bean.spec2 = def.spec2.clone();
            bean.block_type = def.block_type.clone();
            bean.category = def.category.clone();
            bean.color = def.color;
            bean.code = def.code.clone();
            let ptypes = parse_spec_param_types(&def.spec);
            let combined_ptypes = parse_combined_spec_param_types(&def.spec, &def.spec2);
            bean.parameters = resolve_bean_params(header_params, &combined_ptypes, blocks, counter, catalog)
                .into_iter()
                .take(combined_ptypes.len().max(def.param_indices.len()))
                .collect();
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            if let Some(b1) = bodies.first() {
                bean.sub_stack1 = process_js_nested_body_with_catalog(b1, blocks, counter, catalog);
            }
            if bodies.len() > 1 {
                bean.sub_stack2 = process_js_nested_body_with_catalog(&bodies[1], blocks, counter, catalog);
            }
            blocks.push(bean);
            return block_id;
        }
    }

    // 4. Event listener callback references: target.addEventListener("click", fnName);
    if let Some((ev_op, target, event_name, cb_ref)) = extract_event_callback(s) {
        bean.op_code = ev_op.to_string();
        bean.block_type = " ".to_string();
        bean.color = -16615491;
        bean.category = "js_events".to_string();
        bean.spec = if ev_op == "jsRemoveEventOnVar" {
            "%var .removeEventListener( %m.event , %s.inputOnly )"
        } else {
            "%var .addEventListener( %m.event , %s.inputOnly )"
        }.to_string();
        bean.code = if ev_op == "jsRemoveEventOnVar" {
            "%1$s.removeEventListener(\"%2$s\", %3$s);\n"
        } else {
            "%1$s.addEventListener(\"%2$s\", %3$s);\n"
        }.to_string();
        apply_def_meta(&mut bean, &catalog.by_id, ev_op);
        let ptypes = parse_spec_param_types(&bean.spec);
        bean.parameters = resolve_bean_params(vec![target, event_name, cb_ref], &ptypes, blocks, counter, catalog);
        bean.param_types = ptypes.clone();
        bean.tokenized_spec = ptypes;
        blocks.push(bean);
        return block_id;
    }

    // 5. Return / break / continue / inc / dec shortcuts (work both with and without blocks.json)
    let clean_semi = s.trim_end_matches(';').trim();
    if clean_semi == "return" || clean_semi.starts_with("return ") {
        let ret_val = clean_semi.strip_prefix("return").unwrap_or("").trim().to_string();
        bean.op_code = "jsReturn".to_string();
        bean.spec = "return %s.inputOnly".to_string();
        bean.block_type = " ".to_string();
        bean.color = -1683200;
        bean.category = "control".to_string();
        bean.code = "return %1$s;\n".to_string();
        apply_def_meta(&mut bean, &catalog.by_id, "jsReturn");
        let ptypes = parse_spec_param_types(&bean.spec);
        bean.parameters = resolve_bean_params(vec![ret_val], &ptypes, blocks, counter, catalog);
        bean.param_types = ptypes.clone();
        bean.tokenized_spec = ptypes;
        blocks.push(bean);
        return block_id;
    }

    // 6. Scan ALL command statement definitions in blocks.json (DOM, classes, storage, style, console, etc.)
    for def in &catalog.stmt_matchers {
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
                let resolved = resolve_bean_params(params, &ptypes, blocks, counter, catalog);
                bean.parameters = resolved.into_iter().take(ptypes.len().max(def.param_indices.len())).collect();
                bean.param_types = ptypes.clone();
                bean.tokenized_spec = ptypes;
                blocks.push(bean);
                return block_id;
            }
        }
    }

    // 7. Variable declarations: const / let / var (preserving declaration kind + nesting expressions)
    if let Some((decl_kind, vname, vexpr)) = extract_var_declaration_kind(s) {
        let op = if decl_kind == "const" { "jsVarConst" } else { "jsVarLet" };
        bean.op_code = op.to_string();
        bean.category = "js_logic".to_string();
        bean.color = -16742021;
        bean.block_type = " ".to_string();
        bean.spec = if op == "jsVarConst" {
            "const %s.inputOnly = %s.inputOnly"
        } else {
            "let %s.inputOnly = %s.inputOnly"
        }.to_string();
        bean.code = if op == "jsVarConst" {
            "const %1$s = %2$s;\n"
        } else {
            "let %1$s = %2$s;\n"
        }.to_string();
        apply_def_meta(&mut bean, &catalog.by_id, op);
        let ptypes = parse_spec_param_types(&bean.spec);
        let resolved_expr = resolve_js_param_value(&vexpr, "s.inputOnly", blocks, counter, catalog, 0);
        bean.parameters = vec![vname, resolved_expr];
        bean.param_types = ptypes.clone();
        bean.tokenized_spec = ptypes;
        blocks.push(bean);
        return block_id;
    }

    // 8. Increment / decrement: `count++;` / `count--;`
    if (clean_semi.ends_with("++") || clean_semi.ends_with("--")) && clean_semi.len() > 2 {
        let vname = &clean_semi[..clean_semi.len() - 2].trim();
        if is_valid_identifier(vname) {
            let op = if clean_semi.ends_with("++") { "jsVarInc" } else { "jsVarDec" };
            bean.op_code = op.to_string();
            bean.spec = if op == "jsVarInc" { "%var ++" } else { "%var --" }.to_string();
            bean.code = if op == "jsVarInc" { "%1$s++;\n" } else { "%1$s--;\n" }.to_string();
            bean.block_type = " ".to_string();
            bean.category = "js_logic".to_string();
            bean.color = -16742021;
            apply_def_meta(&mut bean, &catalog.by_id, op);
            bean.parameters = vec![vname.to_string()];
            bean.param_types = parse_spec_param_types(&bean.spec);
            bean.tokenized_spec = bean.param_types.clone();
            blocks.push(bean);
            return block_id;
        }
    }

    // 8b. Dot-syntax inline styles: `el.style.prop = val;`
    if let Some((dot_pos, _)) = find_flexible_balanced(clean_semi, 0, ".style.") {
        let target = clean_semi[..dot_pos].trim();
        let after_dot = &clean_semi[dot_pos + ".style.".len()..];
        if let Some(eq_pos) = after_dot.find('=') {
            let prop = after_dot[..eq_pos].trim();
            let val_raw = after_dot[eq_pos + 1..].trim();
            if is_valid_receiver_expr(target) && is_valid_identifier(prop) {
                let (op, raw_params) = match prop {
                    "color" => ("jsSetStyleColor", vec![target.to_string(), val_raw.to_string()]),
                    "background" | "backgroundColor" => ("jsSetStyleBg", vec![target.to_string(), val_raw.to_string()]),
                    "display" => ("jsSetStyleDisplay", vec![target.to_string(), val_raw.to_string()]),
                    "opacity" => ("jsSetStyleOpacity", vec![target.to_string(), val_raw.to_string()]),
                    _ => ("jsSetStyleProp", vec![target.to_string(), prop.to_string(), val_raw.to_string()]),
                };
                if let Some(def) = catalog.by_id.get(op) {
                    apply_def_meta(&mut bean, &catalog.by_id, op);
                    let ptypes = parse_spec_param_types(&def.spec);
                    bean.parameters = resolve_bean_params(raw_params, &ptypes, blocks, counter, catalog);
                    bean.param_types = ptypes.clone();
                    bean.tokenized_spec = ptypes;
                    blocks.push(bean);
                    return block_id;
                }
            }
        }
    }

    // 9. Built-in fallbacks when blocks_json is empty ("[]") in unit tests
    if s.starts_with("console.log(") {
        let arg = extract_paren_arg(s, "console.log");
        bean.op_code = "jsConsoleLog".to_string();
        bean.spec = "console.log( %s )".to_string();
        bean.block_type = " ".to_string();
        bean.color = -13730510;
        bean.category = "js_logic".to_string();
        bean.code = "console.log(%1$s);\n".to_string();
        apply_def_meta(&mut bean, &catalog.by_id, "jsConsoleLog");
        let ptypes = parse_spec_param_types(&bean.spec);
        bean.parameters = resolve_bean_params(vec![arg], &ptypes, blocks, counter, catalog);
        bean.param_types = ptypes.clone();
        bean.tokenized_spec = ptypes;
        blocks.push(bean);
        return block_id;
    }

    if s.starts_with("alert(") {
        let arg = extract_paren_arg(s, "alert");
        bean.op_code = "jsAlert".to_string();
        bean.spec = "alert( %s )".to_string();
        bean.block_type = " ".to_string();
        bean.color = -16743537;
        bean.category = "js_window".to_string();
        bean.code = "alert(%1$s);\n".to_string();
        apply_def_meta(&mut bean, &catalog.by_id, "jsAlert");
        let ptypes = parse_spec_param_types(&bean.spec);
        bean.parameters = resolve_bean_params(vec![arg], &ptypes, blocks, counter, catalog);
        bean.param_types = ptypes.clone();
        bean.tokenized_spec = ptypes;
        blocks.push(bean);
        return block_id;
    }

    if s.contains(".innerHTML") && s.contains('=') {
        if let Some((target, val)) = extract_prop_assignment(s, ".innerHTML") {
            bean.op_code = "jsSetInnerHTML".to_string();
            bean.spec = "%var .innerHTML = %s".to_string();
            bean.block_type = " ".to_string();
            bean.color = -4242420;
            bean.category = "js_dom".to_string();
            bean.code = "%1$s.innerHTML = %2$s;\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsSetInnerHTML");
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![target, val], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            blocks.push(bean);
            return block_id;
        }
    }
    if s.contains(".textContent") && s.contains('=') {
        if let Some((target, val)) = extract_prop_assignment(s, ".textContent") {
            bean.op_code = "jsSetTextContent".to_string();
            bean.spec = "%var .textContent = %s".to_string();
            bean.block_type = " ".to_string();
            bean.color = -4242420;
            bean.category = "js_dom".to_string();
            bean.code = "%1$s.textContent = %2$s;\n".to_string();
            apply_def_meta(&mut bean, &catalog.by_id, "jsSetTextContent");
            let ptypes = parse_spec_param_types(&bean.spec);
            bean.parameters = resolve_bean_params(vec![target, val], &ptypes, blocks, counter, catalog);
            bean.param_types = ptypes.clone();
            bean.tokenized_spec = ptypes;
            blocks.push(bean);
            return block_id;
        }
    }

    // 10. Simple assignment: target = expr (without overriding `=>` or `==`)
    if let Some((target, val)) = extract_simple_assignment(s) {
        bean.op_code = "jsVarAssign".to_string();
        bean.spec = "set %var = %s.inputOnly".to_string();
        bean.block_type = " ".to_string();
        bean.color = -16742021;
        bean.category = "js_logic".to_string();
        bean.code = "%1$s = %2$s;\n".to_string();
        apply_def_meta(&mut bean, &catalog.by_id, "jsVarAssign");
        let ptypes = parse_spec_param_types(&bean.spec);
        bean.parameters = resolve_bean_params(vec![target, val], &ptypes, blocks, counter, catalog);
        bean.param_types = ptypes.clone();
        bean.tokenized_spec = ptypes;
        blocks.push(bean);
        return block_id;
    }

    // 11. Fallback: Add source directly (`asdN` command block " ")
    bean.op_code = "asdN".to_string();
    bean.spec = "Add source directly %s.inputOnly".to_string();
    bean.block_type = " ".to_string();
    bean.color = -12232092;
    bean.category = "asd".to_string();
    let cleaned = s.trim_end_matches(';').trim().to_string();
    bean.parameters = vec![cleaned];
    bean.code = "%1$s\n".to_string();
    if catalog.by_id.contains_key("asdN") {
        apply_def_meta(&mut bean, &catalog.by_id, "asdN");
        bean.block_type = " ".to_string();
    } else if catalog.by_id.contains_key("addSourceCode") {
        apply_def_meta(&mut bean, &catalog.by_id, "addSourceCode");
        bean.block_type = " ".to_string();
    }
    bean.param_types = parse_spec_param_types(&bean.spec);
    bean.tokenized_spec = bean.param_types.clone();
    blocks.push(bean);
    block_id
}

pub fn process_js_nested_body(body: &str, blocks: &mut Vec<DragWebBlockBean>, counter: &mut i32) -> i32 {
    let empty_catalog = JsJsonCatalog {
        container_matchers: Vec::new(),
        stmt_matchers: Vec::new(),
        expr_matchers: Vec::new(),
        by_id: FxHashMap::default(),
    };
    process_js_nested_body_with_catalog(body, blocks, counter, &empty_catalog)
}

pub fn process_js_statement(
    stmt: &str,
    blocks: &mut Vec<DragWebBlockBean>,
    counter: &mut i32,
    is_root: bool,
) -> i32 {
    let empty_catalog = JsJsonCatalog {
        container_matchers: Vec::new(),
        stmt_matchers: Vec::new(),
        expr_matchers: Vec::new(),
        by_id: FxHashMap::default(),
    };
    process_js_statement_with_catalog(stmt, blocks, counter, is_root, if is_root { 0 } else { -1 }, &empty_catalog)
}

pub fn generate_js_blocks(js_code: &str, blocks_json: &str) -> Vec<DragWebBlockBean> {
    let mut blocks = Vec::new();
    let mut counter = 10;
    let catalog = load_js_json_catalog(blocks_json);
    let stmts = split_js_statements(js_code);

    let mut prev_block_id = -1;
    let mut root_idx = 0;
    for stmt in stmts.iter() {
        let is_root = prev_block_id == -1;
        let id = process_js_statement_with_catalog(
            stmt,
            &mut blocks,
            &mut counter,
            is_root,
            root_idx,
            &catalog,
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
