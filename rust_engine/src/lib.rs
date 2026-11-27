pub mod blocks_loader;
pub mod collections;
pub mod generators;
pub mod models;

pub use collections::load_all_langs_json;
pub use generators::css::generate_css_blocks;
pub use generators::html::{generate_dragweb_blocks, generate_html_from_blocks};
pub use generators::js::generate_js_blocks;
pub use models::{BlockPalette, DragWebBlockBean};

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
        assert!(!blocks.is_empty());
    }

    #[test]
    fn test_separate_properties_and_styles() {
        let html = r#"<div id="linear1" class="mybox" style="width: 100px; height: 50px; padding: 8px; margin: 16px; background-color: #ffffff; color: #000000; font-size: 14px;">
  <p id="textview1" class="mytext" style="font-size: 12px; color: #ff0000;">Hello, world!</p>
</div>"#;
        let blocks = generate_dragweb_blocks(html, "[]");
        assert!(!blocks.is_empty());

        let div_block = blocks
            .iter()
            .find(|b| b.parameters.contains(&"div".to_string()))
            .expect("div block must exist");
        assert!(div_block.sub_stack1 >= 0, "div must have attributes/properties in sub_stack1");
        assert!(div_block.sub_stack2 >= 0, "div must have child p in sub_stack2");

        let mut curr_id = div_block.sub_stack1;
        let mut prop_ops = Vec::new();
        while curr_id >= 0 {
            let str_id = curr_id.to_string();
            let b = blocks.iter().find(|x| x.id == str_id).expect("block must exist");
            prop_ops.push(b.op_code.clone());
            curr_id = b.next_block;
        }

        assert!(prop_ops.iter().any(|op| op == "htmlAttrId" || op == "id"), "Must have id block");
        assert!(prop_ops.iter().any(|op| op == "htmlAttrClass" || op == "class"), "Must have class block");
        assert!(prop_ops.iter().any(|op| op == "setWidth" || op.contains("width")), "Must have separated setWidth block");
        assert!(prop_ops.iter().any(|op| op == "setHeight" || op.contains("height")), "Must have separated setHeight block");
        assert!(prop_ops.iter().any(|op| op == "setPadding" || op.contains("padding")), "Must have separated setPadding block");
        assert!(prop_ops.iter().any(|op| op == "setBackgroundColor" || op.contains("background")), "Must have separated setBackgroundColor block");

        let p_block = blocks
            .iter()
            .find(|b| b.parameters.contains(&"p".to_string()))
            .expect("p block must exist");
        assert!(p_block.sub_stack1 >= 0, "p must have sub_stack1");

        let mut p_curr_id = p_block.sub_stack1;
        let mut p_ops = Vec::new();
        while p_curr_id >= 0 {
            let str_id = p_curr_id.to_string();
            let b = blocks.iter().find(|x| x.id == str_id).expect("p prop block must exist");
            p_ops.push(b.op_code.clone());
            if b.op_code == "htmlText" {
                assert_eq!(b.parameters[0], "Hello, world!");
            }
            p_curr_id = b.next_block;
        }
        assert!(p_ops.iter().any(|op| op == "htmlText"), "p must have text property block in sub_stack1");
    }

    #[test]
    fn test_generate_css_blocks() {
        let css = r#"
.card {
    width: 100%;
    height: 200px;
    background-color: #ffffff;
    border-radius: 8px;
    padding: 16px;
}

#btn:hover {
    color: red;
}
"#;
        let blocks = generate_css_blocks(css, "[]");
        assert!(!blocks.is_empty());

        let card_sel = blocks
            .iter()
            .find(|b| b.parameters.contains(&".card".to_string()))
            .expect("card selector exists");
        assert!(card_sel.sub_stack1 >= 0, "card must have properties in sub_stack1");

        let mut curr_id = card_sel.sub_stack1;
        let mut prop_ops = Vec::new();
        while curr_id >= 0 {
            let str_id = curr_id.to_string();
            let b = blocks.iter().find(|x| x.id == str_id).expect("block must exist");
            prop_ops.push(b.op_code.clone());
            curr_id = b.next_block;
        }

        assert!(prop_ops.iter().any(|op| op == "setWidth" || op.contains("width")));
        assert!(prop_ops.iter().any(|op| op == "setHeight" || op.contains("height")));
        assert!(prop_ops.iter().any(|op| op == "setBackgroundColor" || op.contains("background")));
        assert!(prop_ops.iter().any(|op| op == "setRadius" || op.contains("radius")));
        assert!(prop_ops.iter().any(|op| op == "setPadding" || op.contains("padding")));
    }

    #[test]
    fn test_generate_js_blocks() {
        let js = r#"
const title = "DragWeb";
let count = 42;
console.log(title);
if (count > 0) {
    alert("Active");
} else {
    console.log("Zero");
}
document.getElementById("btn").addEventListener("click", function() {
    count = count + 1;
});
"#;
        let blocks = generate_js_blocks(js, "[]");
        assert!(!blocks.is_empty(), "JS blocks must not be empty");

        let title_var = blocks
            .iter()
            .find(|b| b.parameters.contains(&"title".to_string()))
            .expect("title variable block");
        assert_eq!(title_var.op_code, "setVarString");
        assert_eq!(title_var.parameters[1], "DragWeb");

        assert!(blocks.iter().any(|b| b.op_code == "jsConsoleLog"));

        let if_block = blocks.iter().find(|b| b.op_code == "jsIfElse").expect("if-else block");
        assert_eq!(if_block.block_type, "e");
        assert!(if_block.sub_stack1 >= 0, "if sub_stack1 must be valid");
        assert!(if_block.sub_stack2 >= 0, "else sub_stack2 must be valid");

        let event_block = blocks.iter().find(|b| b.op_code == "jsAddEventOnVar").expect("event listener block");
        assert_eq!(event_block.block_type, "c");
        assert!(event_block.sub_stack1 >= 0, "event listener sub_stack1 must be valid");
    }

    #[test]
    fn test_load_blocks_collection() {
        let coll = load_all_langs_json();
        assert!(coll.get("html").is_some());
        assert!(coll.get("css").is_some());
        assert!(coll.get("js").is_some());

        let html_palettes = coll.get("html").unwrap().as_array().unwrap();
        let views_palette = html_palettes
            .iter()
            .find(|p| p.get("name").unwrap().as_str().unwrap() == "Views")
            .unwrap();
        let beans = views_palette.get("beans").unwrap().as_array().unwrap();

        let container_bean = beans
            .iter()
            .find(|b| b.get("header_text").unwrap().as_str().unwrap() == "Container Layout")
            .unwrap();
        assert_eq!(container_bean.get("op_code").unwrap().as_str().unwrap(), "view_convert");
        assert_eq!(container_bean.get("spec").unwrap().as_str().unwrap(), "Convert : %m.Convert");
        assert_eq!(container_bean.get("spec2").unwrap().as_str().unwrap(), "children");
        assert_eq!(container_bean.get("block_type").unwrap().as_str().unwrap(), "e");

        let leaf_bean = beans
            .iter()
            .find(|b| b.get("header_text").unwrap().as_str().unwrap() == "View / Widget")
            .unwrap();
        assert_eq!(leaf_bean.get("op_code").unwrap().as_str().unwrap(), "view_convert_leaf");
        assert_eq!(leaf_bean.get("spec").unwrap().as_str().unwrap(), "Convert : %m.Convert");
        assert_eq!(leaf_bean.get("block_type").unwrap().as_str().unwrap(), "c");

        let ops_palette = html_palettes
            .iter()
            .find(|p| p.get("name").unwrap().as_str().unwrap() == "Operators")
            .unwrap();
        for b in ops_palette.get("beans").unwrap().as_array().unwrap() {
            assert_eq!(b.get("block_type").unwrap().as_str().unwrap(), " ");
        }
    }

    #[test]
    fn test_empty_container_and_html_roundtrip() {
        let html = r#"<section id="hero" data-theme="dark" style="display: flex; flex-wrap: wrap;">
  <div class="empty-box"></div>
  <input type="email" placeholder="Email" required />
  <button disabled>Submit</button>
  <!-- note -->
</section>"#;
        let blocks = generate_dragweb_blocks(html, "[]");
        assert!(!blocks.is_empty());

        let div_block = blocks
            .iter()
            .find(|b| b.parameters.contains(&"div".to_string()))
            .expect("empty div block");
        assert_eq!(div_block.op_code, "view_convert", "empty div must remain container e-block");
        assert_eq!(div_block.spec2, "children");

        let comment_block = blocks
            .iter()
            .find(|b| b.op_code == "html_comment")
            .expect("comment block");
        assert_eq!(comment_block.block_type, " ");

        let required_block = blocks
            .iter()
            .find(|b| b.op_code == "htmlAttrRequired")
            .expect("required boolean attr");
        assert!(required_block.parameters.is_empty());

        let data_block = blocks
            .iter()
            .find(|b| b.op_code == "htmlAttrData")
            .expect("data-* attr");
        assert_eq!(data_block.parameters, vec!["theme", "dark"]);

        let blocks_json = serde_json::to_string(&blocks).unwrap();
        let regenerated = generate_html_from_blocks(&blocks_json);
        assert!(regenerated.contains("<section id=\"hero\" data-theme=\"dark\" style=\"display: flex; flex-wrap: wrap;\">"));
        assert!(regenerated.contains("<div class=\"empty-box\"></div>"));
        assert!(regenerated.contains("<input type=\"email\" placeholder=\"Email\" required />"));
        assert!(regenerated.contains("<button disabled>Submit</button>"));
        assert!(regenerated.contains("<!-- note -->"));
        assert!(regenerated.contains("</section>"));
    }

    #[test]
    fn test_css_import_skip_and_no_normal_block_types() {
        let css = "@import url(\"theme.css\");\n\n#p_1 {\n  color: #333333;\n  padding: 16px;\n}\n";
        let blocks = generate_css_blocks(css, "[]");
        assert!(!blocks.is_empty());
        let sel = blocks.iter().find(|b| b.op_code == "cssSelector").expect("selector block");
        assert_eq!(sel.parameters[0], "#p_1", "@import must not pollute selector");
        for b in &blocks {
            assert_ne!(b.block_type, "normal", "block_type must use space command shape, never normal");
        }
    }
}
