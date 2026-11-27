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
    pub css_prop_map: FxHashMap<String, Vec<BlockDef>>,
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
    FxHashMap<String, Vec<BlockDef>>,
) {
    let mut tag_map = FxHashMap::default();
    let mut attr_map = FxHashMap::default();
    let mut css_prop_map: FxHashMap<String, Vec<BlockDef>> = FxHashMap::default();

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
        ("width", "setWidth", "width %d %m.unit", "width: %1$s%2$s;", -1086464),
        ("height", "setHeight", "height %d %m.unit", "height: %1$s%2$s;", -1086464),
        ("min-width", "setMinWidth", "min-width %d %m.unit", "min-width: %1$s%2$s;", -1086464),
        ("max-width", "setMaxWidth", "max-width %d %m.unit", "max-width: %1$s%2$s;", -1086464),
        ("min-height", "setMinHeight", "min-height %d %m.unit", "min-height: %1$s%2$s;", -1086464),
        ("max-height", "setMaxHeight", "max-height %d %m.unit", "max-height: %1$s%2$s;", -1086464),
        ("margin", "setMargin", "margin %d %m.unit", "margin: %1$s%2$s;", -1086464),
        ("margin-top", "setMarginTop", "margin-top %d %m.unit", "margin-top: %1$s%2$s;", -1086464),
        ("margin-bottom", "setMarginBottom", "margin-bottom %d %m.unit", "margin-bottom: %1$s%2$s;", -1086464),
        ("margin-left", "setMarginLeft", "margin-left %d %m.unit", "margin-left: %1$s%2$s;", -1086464),
        ("margin-right", "setMarginRight", "margin-right %d %m.unit", "margin-right: %1$s%2$s;", -1086464),
        ("padding", "setPadding", "padding %d %m.unit", "padding: %1$s%2$s;", -1086464),
        ("padding-top", "setPaddingTop", "padding-top %d %m.unit", "padding-top: %1$s%2$s;", -1086464),
        ("padding-bottom", "setPaddingBottom", "padding-bottom %d %m.unit", "padding-bottom: %1$s%2$s;", -1086464),
        ("padding-left", "setPaddingLeft", "padding-left %d %m.unit", "padding-left: %1$s%2$s;", -1086464),
        ("padding-right", "setPaddingRight", "padding-right %d %m.unit", "padding-right: %1$s%2$s;", -1086464),
        ("display", "setDisplay", "display: %m.display", "display: %1$s;", -1086464),
        ("position", "setPosition", "position: %m.position", "position: %1$s;", -1086464),
        ("overflow", "setOverflow", "overflow: %m.overflow", "overflow: %1$s;", -1086464),
        ("gap", "setGap", "gap %d %m.unit", "gap: %1$s%2$s;", -1086464),
        ("flex-direction", "setFlexDirection", "flex-direction: %m.flexDirection", "flex-direction: %1$s;", -1086464),
        ("flex-wrap", "setFlexWrap", "flex-wrap: %m.flexWrap", "flex-wrap: %1$s;", -1086464),
        ("justify-content", "setJustifyContent", "justify-content: %m.justifyContent", "justify-content: %1$s;", -1086464),
        ("align-items", "setAlignItems", "align-items: %m.alignItems", "align-items: %1$s;", -1086464),
        ("color", "setColor", "color %m.color", "color: %1$s;", -13022805),
        ("font-size", "setFontSize", "font-size %d %m.unit", "font-size: %1$s%2$s;", -13022805),
        ("font-weight", "setFontWeight", "font-weight: %m.fontWeight", "font-weight: %1$s;", -13022805),
        ("text-align", "setTextAlign", "text-align: %m.textAlign", "text-align: %1$s;", -13022805),
        ("text-decoration", "setTextDecoration", "text-decoration: %m.textDecoration", "text-decoration: %1$s;", -13022805),
        ("text-transform", "setTextTransform", "text-transform: %m.textTransform", "text-transform: %1$s;", -13022805),
        ("line-height", "setLineHeight", "line-height: %s", "line-height: %1$s;", -13022805),
        ("font-family", "setFontFamily", "font-family: %s", "font-family: %1$s;", -13022805),
        ("background-color", "setBackgroundColor", "background-color %m.color", "background-color: %1$s;", -9920712),
        ("background", "setBackground", "background %m.color", "background: %1$s;", -9920712),
        ("background", "setBackgroundRaw", "background %s.inputOnly", "background: %1$s;", -9920712),
        ("border", "setBorder", "border %d %m.unit %m.borderStyle %m.color", "border: %1$s%2$s %3$s %4$s;", -9920712),
        ("border", "setBorderNone", "border none", "border: none;", -9920712),
        ("border-radius", "setRadius", "border-radius %d %m.unit", "border-radius: %1$s%2$s;", -9920712),
        ("border-radius", "setRadiusMulti", "border-radius TL %d %m.unit TR %d %m.unit BR %d %m.unit BL %d %m.unit", "border-radius: %1$s%2$s %3$s%4$s %5$s%6$s %7$s%8$s;", -9920712),
        ("box-shadow", "setBoxShadow", "box-shadow x %d px y %d px blur %d px color %m.color", "box-shadow: %1$spx %2$spx %3$spx %4$s;", -6447836),
        ("box-shadow", "setBoxShadowRaw", "box-shadow %s.inputOnly", "box-shadow: %1$s;", -6447836),
        ("opacity", "setOpacity", "opacity %d", "opacity: %1$s;", -16611119),
        ("cursor", "setCursor", "cursor %m.cursor", "cursor: %1$s;", -15108462),
    ];

    for (k, id, spec, code, color) in default_css_props {
        css_prop_map.entry(k.to_string()).or_default().push(BlockDef {
            id: id.to_string(),
            category: if color == -1086464 {
                "css_layout".to_string()
            } else if color == -13022805 {
                "css_text".to_string()
            } else {
                "css_background".to_string()
            },
            block_type: " ".to_string(),
            color,
            spec: spec.to_string(),
            spec2: String::new(),
            code: code.to_string(),
        });
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
                && !id.starts_with("comment")
            {
                let mut inserted = false;
                if let Some(colon_pos) = code.find(':') {
                    let code_key = code[..colon_pos].trim().to_lowercase();
                    if !code_key.is_empty() && !code_key.contains('%') && !code_key.contains(' ') {
                        let list = css_prop_map.entry(code_key).or_default();
                        list.retain(|existing| existing.id != def.id);
                        // Prefer blocks.json definition by inserting at front
                        list.insert(0, def.clone());
                        inserted = true;
                    }
                }
                if !inserted {
                    let raw_name = id.trim_start_matches("css_").trim_start_matches("set");
                    let mut kebab = String::new();
                    for (ci, ch) in raw_name.chars().enumerate() {
                        if ch.is_uppercase() && ci > 0 {
                            kebab.push('-');
                        }
                        kebab.push(ch.to_ascii_lowercase());
                    }
                    if !kebab.is_empty() {
                        let list = css_prop_map.entry(kebab).or_default();
                        list.retain(|existing| existing.id != def.id);
                        list.insert(0, def);
                    }
                }
            }
        }
    }

    for list in css_prop_map.values_mut() {
        list.sort_by_key(css_def_specificity);
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

fn split_number_and_unit(token: &str) -> Option<(String, String)> {
    let t = token.trim();
    if t.is_empty() {
        return None;
    }
    if t.eq_ignore_ascii_case("auto") {
        return Some(("0".to_string(), "auto".to_string()));
    }
    let bytes = t.as_bytes();
    let mut i = 0;
    if i < bytes.len() && (bytes[i] == b'+' || bytes[i] == b'-') {
        i += 1;
    }
    let digit_start = i;
    let mut has_digit = false;
    while i < bytes.len() && (bytes[i].is_ascii_digit() || bytes[i] == b'.') {
        if bytes[i].is_ascii_digit() {
            has_digit = true;
        }
        i += 1;
    }
    if !has_digit || i == digit_start {
        return None;
    }
    let num = t[..i].to_string();
    let unit = t[i..].trim();
    let final_unit = if unit.is_empty() {
        if num == "0" { "px".to_string() } else { "".to_string() }
    } else {
        unit.to_string()
    };
    Some((num, final_unit))
}

fn css_def_specificity(def: &BlockDef) -> i32 {
    if def.id.ends_with("Raw") || def.id.ends_with("Multi") && def.id != "setRadiusMulti" {
        return 30;
    }
    let param_types = parse_spec_param_types(&def.spec);
    if param_types.is_empty()
        || def.id == "setBackgroundImage"
        || def.id == "filterBlur"
        || def.id == "filterDropShadow"
    {
        return 0;
    }
    if def.id == "setRadiusMulti"
        || def.id == "setBoxShadowSpread"
        || def.id == "setBoxShadow"
        || def.id == "setBorder"
        || def.id == "setOutline"
        || (param_types.len() == 2 && def.spec.contains("%d %m.unit"))
    {
        return 10;
    }
    if param_types.iter().any(|p| p.starts_with("m.") || p == "d" || p == "color") {
        return 20;
    }
    25
}

fn split_css_tokens(val: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    let mut paren_depth: i32 = 0;
    let mut quote: Option<char> = None;

    for ch in val.chars() {
        if let Some(q) = quote {
            cur.push(ch);
            if ch == q {
                quote = None;
            }
            continue;
        }
        if ch == '\'' || ch == '"' {
            quote = Some(ch);
            cur.push(ch);
            continue;
        }
        if ch == '(' {
            paren_depth += 1;
            cur.push(ch);
            continue;
        }
        if ch == ')' {
            if paren_depth > 0 {
                paren_depth -= 1;
            }
            cur.push(ch);
            continue;
        }
        if ch.is_whitespace() && paren_depth == 0 {
            if !cur.is_empty() {
                out.push(std::mem::take(&mut cur));
            }
            continue;
        }
        cur.push(ch);
    }
    if !cur.is_empty() {
        out.push(cur);
    }
    out
}

fn is_css_color_token(tok: &str) -> bool {
    let t = tok.trim();
    let lower = t.to_ascii_lowercase();
    if lower.starts_with('#') {
        let hex = &lower[1..];
        return matches!(hex.len(), 3 | 4 | 6 | 8) && hex.chars().all(|c| c.is_ascii_hexdigit());
    }
    if lower.starts_with("rgb(")
        || lower.starts_with("rgba(")
        || lower.starts_with("hsl(")
        || lower.starts_with("hsla(")
        || lower.starts_with("var(")
    {
        return true;
    }
    matches!(
        lower.as_str(),
        "transparent"
            | "currentcolor"
            | "black"
            | "white"
            | "red"
            | "green"
            | "blue"
            | "yellow"
            | "orange"
            | "pink"
            | "purple"
            | "brown"
            | "gray"
            | "grey"
            | "silver"
            | "gold"
            | "cyan"
            | "magenta"
            | "teal"
            | "navy"
            | "maroon"
            | "olive"
            | "lime"
            | "aqua"
            | "indigo"
            | "violet"
            | "coral"
            | "crimson"
            | "inherit"
            | "initial"
            | "unset"
    )
}

fn is_border_style_token(tok: &str) -> bool {
    matches!(
        tok.trim().to_ascii_lowercase().as_str(),
        "none" | "solid" | "dashed" | "dotted" | "double" | "groove" | "ridge" | "inset" | "outset" | "hidden"
    )
}

fn strip_px_number(tok: &str) -> Option<String> {
    let (n, u) = split_number_and_unit(tok)?;
    if n == "0" || u.is_empty() || u.eq_ignore_ascii_case("px") {
        Some(n)
    } else {
        None
    }
}

fn match_css_def_and_params(def: &BlockDef, key: &str, val: &str) -> Option<Vec<String>> {
    let param_types = parse_spec_param_types(&def.spec);
    let trimmed = val.trim();
    let tokens = split_css_tokens(trimmed);

    if param_types.is_empty() {
        let expected_val = def
            .code
            .split(':')
            .nth(1)
            .map(|s| s.trim().trim_end_matches(';').trim())
            .unwrap_or("");
        if !expected_val.is_empty() && trimmed.eq_ignore_ascii_case(expected_val) {
            return Some(Vec::new());
        }
        return None;
    }

    if def.id == "setRadiusMulti" || (key == "border-radius" && param_types.len() == 8) {
        if tokens.len() != 4 {
            return None;
        }
        let mut params = Vec::with_capacity(8);
        for tok in &tokens {
            let (n, u) = split_number_and_unit(tok)?;
            params.push(n);
            params.push(if u.is_empty() { "px".to_string() } else { u });
        }
        return Some(params);
    }

    if def.id == "setBackgroundImage" {
        let lower = trimmed.to_ascii_lowercase();
        if lower.starts_with("url(") && trimmed.ends_with(')') {
            let inner = trimmed[4..trimmed.len() - 1]
                .trim()
                .trim_matches('"')
                .trim_matches('\'')
                .to_string();
            return Some(vec![inner]);
        }
        return None;
    }

    if def.id == "filterBlur" {
        let lower = trimmed.to_ascii_lowercase();
        if lower.starts_with("blur(") && trimmed.ends_with(')') {
            let inner = trimmed[5..trimmed.len() - 1].trim();
            if let Some(n) = strip_px_number(inner) {
                return Some(vec![n]);
            }
        }
        return None;
    }

    if (def.id == "setBorder" || def.id == "setOutline") && (param_types.len() == 3 || param_types.len() == 4) {
        if tokens.len() >= 2 && tokens.len() <= 3 {
            let mut width_pair: Option<(String, String)> = None;
            let mut style_tok: Option<String> = None;
            let mut color_tok: Option<String> = None;
            let mut valid = true;
            for tok in &tokens {
                if style_tok.is_none() && is_border_style_token(tok) {
                    style_tok = Some(tok.to_ascii_lowercase());
                } else if width_pair.is_none() {
                    if let Some((n, u)) = split_number_and_unit(tok) {
                        width_pair = Some((n, if u.is_empty() { "px".to_string() } else { u }));
                        continue;
                    } else if color_tok.is_none() && is_css_color_token(tok) {
                        color_tok = Some(tok.to_string());
                    } else {
                        valid = false;
                        break;
                    }
                } else if color_tok.is_none() && is_css_color_token(tok) {
                    color_tok = Some(tok.to_string());
                } else {
                    valid = false;
                    break;
                }
            }
            if valid {
                if let (Some((n, u)), Some(st)) = (width_pair, style_tok) {
                    let col = color_tok.unwrap_or_else(|| "currentColor".to_string());
                    if param_types.len() == 4 {
                        return Some(vec![n, u, st, col]);
                    } else if st == "solid" {
                        return Some(vec![n, u, col]);
                    }
                }
            }
        }
        return None;
    }

    if def.id == "setBoxShadow" || def.id == "setBoxShadowSpread" {
        if trimmed.contains("),") || (trimmed.contains(',') && !trimmed.contains('(')) {
            return None;
        }
        let mut lengths: Vec<String> = Vec::new();
        let mut color_tok: Option<String> = None;
        let mut is_inset = false;
        let mut valid = true;
        for tok in &tokens {
            if tok.eq_ignore_ascii_case("inset") {
                is_inset = true;
            } else if is_css_color_token(tok) && color_tok.is_none() {
                color_tok = Some(tok.to_string());
            } else if let Some(n) = strip_px_number(tok) {
                lengths.push(n);
            } else {
                valid = false;
                break;
            }
        }
        if valid && !lengths.is_empty() && color_tok.is_some() {
            let x = lengths[0].clone();
            let y = lengths.get(1).cloned().unwrap_or_else(|| "0".to_string());
            let blur = lengths.get(2).cloned().unwrap_or_else(|| "0".to_string());
            let col = color_tok.unwrap();
            if def.id == "setBoxShadow" && lengths.len() <= 3 && !is_inset && param_types.len() == 4 {
                return Some(vec![x, y, blur, col]);
            }
            if def.id == "setBoxShadowSpread" && lengths.len() <= 4 && (lengths.len() == 4 || is_inset) && param_types.len() == 6 {
                let spread = lengths.get(3).cloned().unwrap_or_else(|| "0".to_string());
                let stype = if is_inset { "inset" } else { "outline" }.to_string();
                return Some(vec![x, y, blur, spread, col, stype]);
            }
        }
        return None;
    }

    if (def.id == "setMarginMulti" || def.id == "setPaddingMulti") && tokens.len() <= 1 {
        return None;
    }

    if param_types.len() == 2 && def.spec.contains("%d %m.unit") {
        if tokens.len() != 1 {
            return None;
        }
        if tokens[0].eq_ignore_ascii_case("auto") {
            return Some(vec![String::new(), "auto".to_string()]);
        }
        let (n, u) = split_number_and_unit(&tokens[0])?;
        return Some(vec![n, if u.is_empty() { "px".to_string() } else { u }]);
    }

    if param_types.len() == 1 {
        if param_types[0] == "m.color" {
            return if is_css_color_token(trimmed) {
                Some(vec![trimmed.to_string()])
            } else {
                None
            };
        }
        if param_types[0] == "d" {
            return if trimmed.parse::<f64>().is_ok() {
                Some(vec![trimmed.to_string()])
            } else {
                None
            };
        }
        return Some(vec![trimmed.to_string()]);
    }

    None
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

            let mut matched = false;
            if let Some(defs) = ctx.css_prop_map.get(&key) {
                for def in defs {
                    if let Some(params) = match_css_def_and_params(def, &key, val) {
                        prop_block.op_code = def.id.clone();
                        prop_block.spec = def.spec.clone();
                        prop_block.code = def.code.clone();
                        prop_block.category = def.category.clone();
                        prop_block.block_type = def.block_type.clone();
                        prop_block.color = def.color;
                        prop_block.parameters = params;
                        prop_block.param_types = parse_spec_param_types(&def.spec);
                        prop_block.tokenized_spec = prop_block.param_types.clone();
                        matched = true;
                        break;
                    }
                }
            }

            if !matched {
                let color = if key.contains("width") || key.contains("height") || key.contains("margin") || key.contains("padding") || key.contains("display") || key.contains("position") {
                    -16611119 // #0288D1
                } else if key.contains("color") || key.contains("font") || key.contains("text") || key.contains("line-height") {
                    -13070788 // #388E3C
                } else {
                    -9920712 // #689F38
                };

                prop_block.op_code = "cssPropertyGeneric".to_string();
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

fn combine_unit_pair(num: &str, unit: &str) -> String {
    let n = num.trim();
    let u = unit.trim();
    if u.eq_ignore_ascii_case("auto") {
        return "auto".to_string();
    }
    if n.is_empty() {
        return u.to_string();
    }
    format!("{}{}", n, u)
}

fn css_prop_from_block(block: &DragWebBlockBean) -> Option<String> {
    let op = block.op_code.as_str();
    let p0 = block.parameters.first().map(|s| s.trim()).unwrap_or("");
    let p1 = block.parameters.get(1).map(|s| s.trim()).unwrap_or("");

    if op == "setRadiusMulti" && block.parameters.len() >= 8 {
        let tl = combine_unit_pair(&block.parameters[0], &block.parameters[1]);
        let tr = combine_unit_pair(&block.parameters[2], &block.parameters[3]);
        let br = combine_unit_pair(&block.parameters[4], &block.parameters[5]);
        let bl = combine_unit_pair(&block.parameters[6], &block.parameters[7]);
        return Some(format!("border-radius: {} {} {} {};", tl, tr, br, bl));
    }

    if op == "setBorderNone" {
        return Some("border: none;".to_string());
    }
    if op == "setOutlineNone" {
        return Some("outline: none;".to_string());
    }
    if op == "setBoxShadowNone" {
        return Some("box-shadow: none;".to_string());
    }
    if op == "filterNone" {
        return Some("filter: none;".to_string());
    }

    if (op == "setBorder" || op == "setOutline") && block.parameters.len() >= 4 && block.spec.contains("%m.unit") {
        let pname = if op == "setBorder" { "border" } else { "outline" };
        let w = combine_unit_pair(p0, p1);
        let style = block.parameters[2].trim();
        let c = block.parameters[3].trim();
        return Some(format!("{}: {} {} {};", pname, w, style, c));
    }

    if (op == "setBorder" || op == "setOutline") && block.parameters.len() >= 3 && block.spec.contains("%m.unit") {
        let pname = if op == "setBorder" { "border" } else { "outline" };
        let w = combine_unit_pair(p0, p1);
        let c = block.parameters[2].trim();
        return Some(format!("{}: {} solid {};", pname, w, c));
    }

    if op == "setBoxShadow" && block.parameters.len() >= 4 {
        return Some(format!(
            "box-shadow: {}px {}px {}px {};",
            block.parameters[0].trim(),
            block.parameters[1].trim(),
            block.parameters[2].trim(),
            block.parameters[3].trim()
        ));
    }
    if op == "setBoxShadowSpread" && block.parameters.len() >= 6 {
        let stype = if block.parameters[5].trim() == "inset" { " inset" } else { "" };
        return Some(format!(
            "box-shadow: {}px {}px {}px {}px {}{};",
            block.parameters[0].trim(),
            block.parameters[1].trim(),
            block.parameters[2].trim(),
            block.parameters[3].trim(),
            block.parameters[4].trim(),
            stype
        ));
    }

    let prop_name = match op {
        "setWidth" => Some("width"),
        "setHeight" => Some("height"),
        "setMinWidth" => Some("min-width"),
        "setMaxWidth" => Some("max-width"),
        "setMinHeight" => Some("min-height"),
        "setMaxHeight" => Some("max-height"),
        "setMargin" | "setMarginMulti" => Some("margin"),
        "setMarginTop" => Some("margin-top"),
        "setMarginBottom" => Some("margin-bottom"),
        "setMarginLeft" => Some("margin-left"),
        "setMarginRight" => Some("margin-right"),
        "setPadding" | "setPaddingMulti" => Some("padding"),
        "setPaddingTop" => Some("padding-top"),
        "setPaddingBottom" => Some("padding-bottom"),
        "setPaddingLeft" => Some("padding-left"),
        "setPaddingRight" => Some("padding-right"),
        "setDisplay" => Some("display"),
        "setPosition" => Some("position"),
        "setOverflow" => Some("overflow"),
        "setGap" => Some("gap"),
        "setTop" => Some("top"),
        "setBottom" => Some("bottom"),
        "setLeft" => Some("left"),
        "setRight" => Some("right"),
        "setFlexDirection" => Some("flex-direction"),
        "setFlexWrap" => Some("flex-wrap"),
        "setJustifyContent" => Some("justify-content"),
        "setAlignItems" => Some("align-items"),
        "setAlignSelf" => Some("align-self"),
        "setColor" => Some("color"),
        "setFontSize" => Some("font-size"),
        "setFontWeight" => Some("font-weight"),
        "setTextAlign" => Some("text-align"),
        "setTextDecoration" => Some("text-decoration"),
        "setTextTransform" => Some("text-transform"),
        "setLineHeight" => Some("line-height"),
        "setLetterSpacing" => Some("letter-spacing"),
        "setFontFamily" => Some("font-family"),
        "setBackgroundColor" => Some("background-color"),
        "setBackground" | "setBackgroundRaw" => Some("background"),
        "setBorder" | "setBorderRaw" => Some("border"),
        "setOutline" => Some("outline"),
        "setRadius" => Some("border-radius"),
        "setBoxShadow" | "setBoxShadowRaw" => Some("box-shadow"),
        "setOpacity" => Some("opacity"),
        "setCursor" => Some("cursor"),
        _ => None,
    };

    if let Some(name) = prop_name {
        if p0.is_empty() && !block.spec.contains("%m.unit") {
            return None;
        }
        let val = if block.parameters.len() >= 2 && block.spec.contains("%m.unit") {
            combine_unit_pair(p0, p1)
        } else {
            p0.to_string()
        };
        if val.is_empty() {
            return None;
        }
        return Some(format!("{}: {};", name, val));
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
