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
        assert_eq!(title_var.op_code, "jsVarConst");
        assert_eq!(title_var.parameters[1], "\"DragWeb\"");

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

    #[test]
    fn test_unit_blocks_and_single_vs_multi_border_radius() {
        let html_single = "<div style=\"width: 100%; padding: 16px; border-radius: 8px;\">Hi</div>";
        let blocks_single = generate_dragweb_blocks(html_single, "[]");
        let radius_single = blocks_single
            .iter()
            .find(|b| b.op_code == "setRadius")
            .expect("single border-radius must select setRadius, not setRadiusMulti");
        assert_eq!(radius_single.parameters, vec!["8", "px"]);
        assert!(
            blocks_single.iter().all(|b| b.op_code != "setRadiusMulti"),
            "single-value border-radius must not produce setRadiusMulti"
        );
        let width_blk = blocks_single.iter().find(|b| b.op_code == "setWidth").expect("setWidth");
        assert_eq!(width_blk.parameters, vec!["100", "%"]);
        let pad_blk = blocks_single.iter().find(|b| b.op_code == "setPadding").expect("setPadding");
        assert_eq!(pad_blk.parameters, vec!["16", "px"]);

        let json_single = serde_json::to_string(&blocks_single).unwrap();
        let regenerated_single = generate_html_from_blocks(&json_single);
        assert!(regenerated_single.contains("border-radius: 8px"), "{}", regenerated_single);
        assert!(regenerated_single.contains("width: 100%"), "{}", regenerated_single);
        assert!(regenerated_single.contains("padding: 16px"), "{}", regenerated_single);

        let html_multi = "<div style=\"border-radius: 4px 8px 12px 16px;\">Hi</div>";
        let blocks_multi = generate_dragweb_blocks(html_multi, "[]");
        let radius_multi = blocks_multi
            .iter()
            .find(|b| b.op_code == "setRadiusMulti")
            .expect("4-corner border-radius must select setRadiusMulti");
        assert_eq!(
            radius_multi.parameters,
            vec!["4", "px", "8", "px", "12", "px", "16", "px"]
        );
        let json_multi = serde_json::to_string(&blocks_multi).unwrap();
        let regenerated_multi = generate_html_from_blocks(&json_multi);
        assert!(
            regenerated_multi.contains("border-radius: 4px 8px 12px 16px"),
            "{}",
            regenerated_multi
        );
    }

    #[test]
    fn test_js_blocks_json_reference_scan() {
        let real_blocks_json = include_str!("../../app/src/main/assets/blocks.json");
        let js = r#"
// Initialize card
const btn = document.getElementById("submitBtn");
localStorage.setItem("theme", "dark");
setTimeout(() => {
  console.log("ready");
}, 250);
"#;
        let blocks = generate_js_blocks(js, real_blocks_json);
        let comment = blocks.iter().find(|b| b.op_code == "commentBlock").expect("commentBlock");
        assert_eq!(comment.parameters, vec!["Initialize card"]);
        let get_id = blocks.iter().find(|b| b.op_code == "jsConstGetById").expect("jsConstGetById");
        assert_eq!(get_id.parameters, vec!["btn", "submitBtn"]);
        let ls = blocks.iter().find(|b| b.op_code == "jsLocalStorageSet").expect("jsLocalStorageSet");
        assert_eq!(ls.parameters, vec!["theme", "dark"]);
        let timeout = blocks.iter().find(|b| b.op_code == "jsSetTimeout").expect("jsSetTimeout");
        assert_eq!(timeout.parameters, vec!["250"]);
        assert_eq!(timeout.block_type, "c");
        assert!(timeout.sub_stack1 >= 0);
    }

    #[test]
    fn test_todo_app_js_function_callback_and_nesting() {
        let real_blocks_json = include_str!("../../app/src/main/assets/blocks.json");
        let js = r#"
document.getElementById("addBtn").addEventListener("click", addTask);

function addTask() {
  // Read user's input cleanly
  const input = document.getElementById("taskInput");
  const taskText = input.value.trim();
  if (taskText === "") return;
  const li = document.createElement("li");
  li.textContent = taskText;
  li.addEventListener("click", () => {
    li.classList.toggle("completed");
  });
  document.getElementById("taskList").appendChild(li);
  input.value = "";
  deleteBtn.onclick = () => li.remove();
}
"#;
        let blocks = generate_js_blocks(js, real_blocks_json);

        let cb = blocks
            .iter()
            .find(|b| b.op_code == "jsAddEventCallback")
            .expect("document.getElementById('addBtn').addEventListener('click', addTask) must use jsAddEventCallback");
        assert_eq!(cb.parameters[1], "click");
        assert_eq!(cb.parameters[2], "addTask");
        assert!(cb.parameters[0].starts_with('@'), "must nest jsGetById: {:?}", cb.parameters);

        let fn_blk = blocks
            .iter()
            .find(|b| b.op_code == "jsFunctionDefine")
            .expect("function addTask() must produce jsFunctionDefine container");
        assert_eq!(fn_blk.parameters, vec!["addTask", ""]);
        assert!(fn_blk.sub_stack1 >= 0);

        assert!(blocks.iter().any(|b| b.op_code == "jsConstGetById" && b.parameters == vec!["input", "taskInput"]));
        assert!(blocks.iter().any(|b| b.op_code == "jsVarConst" && b.parameters[0] == "taskText"));
        assert!(blocks.iter().any(|b| b.op_code == "jsStringTrim"));
        assert!(blocks.iter().any(|b| b.op_code == "jsGetValue" && b.parameters == vec!["input"]));
        assert!(blocks.iter().any(|b| b.op_code == "jsIf" && b.sub_stack1 >= 0));
        assert!(blocks.iter().any(|b| b.op_code == "jsReturn"));
        assert!(blocks.iter().any(|b| b.op_code == "jsCreateElement" && b.parameters == vec!["li"]));
        assert!(blocks.iter().any(|b| b.op_code == "jsSetTextContent" && b.parameters[0] == "li" && b.parameters[1].starts_with('@')));
        assert!(blocks.iter().any(|b| b.op_code == "jsVarRef" && b.parameters == vec!["taskText"]));
        assert!(blocks.iter().any(|b| b.op_code == "jsToggleClass" && b.parameters == vec!["li", "completed"]));
        assert!(blocks.iter().any(|b| b.op_code == "jsAppendChild" && b.parameters[0].starts_with('@') && b.parameters[1] == "li"));
        assert!(blocks.iter().any(|b| b.op_code == "jsSetValue" && b.parameters == vec!["input", ""]));
        assert!(
            blocks.iter().any(|b| b.op_code == "asdN" && b.parameters[0] == "deleteBtn.onclick = () => li.remove()"),
            "braceless arrow assignment must not be hijacked by jsRemoveElement: {:?}",
            blocks.iter().map(|b| (&b.op_code, &b.parameters)).collect::<Vec<_>>()
        );
        assert!(
            blocks.iter().all(|b| b.op_code != "asdN" || b.parameters[0].contains("deleteBtn.onclick")),
            "only deleteBtn.onclick should fall back to asdN, got: {:?}",
            blocks.iter().filter(|b| b.op_code == "asdN").map(|b| &b.parameters).collect::<Vec<_>>()
        );
    }

    #[test]
    fn test_todo_app_css_color_padding_border_and_shadow() {
        let real_blocks_json = include_str!("../../app/src/main/assets/blocks.json");
        let css = r#"
body {
  font-family: Arial, sans-serif;
  background: #f4f4f4;
  display: flex;
  justify-content: center;
  height: 100vh;
}
.todo-container {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  width: 300px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
}
input[type="text"] {
  width: 75%;
  padding: 8px;
  border: 1px solid #ccc;
}
button {
  padding: 8px;
  border: none;
  background: #007bff;
}
"#;
        let blocks = generate_css_blocks(css, real_blocks_json);
        let bg_blocks: Vec<_> = blocks.iter().filter(|b| b.op_code == "setBackground").collect();
        assert_eq!(bg_blocks.len(), 3);
        for bg in bg_blocks {
            assert_eq!(bg.color, -9920712, "background must use green #689F38, never purple");
            assert_eq!(bg.param_types, vec!["m.color"]);
        }

        let pad_blocks: Vec<_> = blocks.iter().filter(|b| b.op_code == "setPadding").collect();
        assert_eq!(pad_blocks.len(), 3, "single-value padding must use setPadding, never setPaddingMulti");
        assert!(blocks.iter().all(|b| b.op_code != "setPaddingMulti"));

        let shadow = blocks.iter().find(|b| b.op_code == "setBoxShadow").expect("setBoxShadow");
        assert_eq!(shadow.parameters, vec!["0", "4", "6", "rgba(0,0,0,0.1)"]);
        assert_eq!(shadow.param_types, vec!["d", "d", "d", "m.color"]);

        let border = blocks.iter().find(|b| b.op_code == "setBorder").expect("setBorder");
        assert_eq!(border.parameters, vec!["1", "px", "solid", "#ccc"]);

        assert!(blocks.iter().any(|b| b.op_code == "setBorderNone"), "border: none must use setBorderNone");
        assert!(blocks.iter().all(|b| b.op_code != "asdN"), "no asdN expected in clean CSS");
    }

    #[test]
    fn test_css_full_json_scan_control_asd_and_stacking() {
        let real_blocks_json = include_str!("../../app/src/main/assets/blocks.json");
        let css = r#"
@import url("theme.css");
/*===== Layout Cards =====*/
@keyframes fadeUp {
  0% { opacity: 0; }
  100% { opacity: 1; }
}
.card:not(.hidden) {
  margin: 12px 24px;
  --custom-token: 42;
}
.btn:hover {
  color: #ffffff;
}
@media (max-width: 768px) {
  .btn { padding: 8px; }
}
@font-face {
  font-family: "Custom";
}
"#;
        let blocks = generate_css_blocks(css, real_blocks_json);
        let bold_comment = blocks
            .iter()
            .find(|b| b.op_code == "commentBlockBold")
            .expect("commentBlockBold from blocks.json");
        assert_eq!(bold_comment.parameters, vec!["Layout Cards"]);
        assert!(bold_comment.is_connected_to_root);
        assert!(bold_comment.next_block >= 0, "top-level CSS blocks must stack sequentially");

        let kf = blocks
            .iter()
            .find(|b| b.op_code == "cssKeyframes")
            .expect("cssKeyframes from blocks.json");
        assert_eq!(kf.parameters, vec!["fadeUp"]);
        assert!(kf.sub_stack1 >= 0);
        assert!(blocks.iter().any(|b| b.op_code == "cssKeyframeStep"));

        let sel_s = blocks
            .iter()
            .find(|b| b.op_code == "cssSelectorS")
            .expect("cssSelectorS for .card:not(.hidden)");
        assert_eq!(sel_s.parameters[0], ".card");
        assert!(sel_s.parameters[1].starts_with('@'), "must stack cssCNot reporter block: {:?}", sel_s.parameters);
        let not_blk = blocks
            .iter()
            .find(|b| b.op_code == "cssCNot")
            .expect("cssCNot stacked block");
        assert_eq!(not_blk.parameters, vec![".hidden"]);
        assert!(!not_blk.is_connected_to_root);

        let margin_blk = blocks
            .iter()
            .find(|b| b.op_code == "setMarginMulti")
            .expect("setMarginMulti for 2-value shorthand");
        assert!(margin_blk.parameters[0].starts_with('@'), "must stack cssCUnits inside margin");
        let units_blk = blocks
            .iter()
            .find(|b| b.op_code == "cssCUnits")
            .expect("cssCUnits stacked block");
        assert_eq!(units_blk.parameters, vec!["12", "px", "24", "px"]);

        assert!(blocks.iter().any(|b| b.op_code == "asdN" && b.parameters[0].contains("--custom-token")));
        assert!(blocks.iter().any(|b| b.op_code == "cssHover"));
        assert!(blocks.iter().any(|b| b.op_code == "cssMediaQuery" && b.parameters[0] == "768px"));
        assert!(blocks.iter().any(|b| b.op_code == "asdN" && b.parameters[0].contains("@font-face")));
    }

    #[test]
    fn test_js_control_containers_operators_and_asd_stacking() {
        let real_blocks_json = include_str!("../../app/src/main/assets/blocks.json");
        let js = r#"
try {
  if (count > 5 && !disabled) {
    items.forEach(item => {
      console.log(item);
    });
  }
} catch (err) {
  customUnhandledHelper(err);
}
window.addEventListener('scroll', function() {
  console.log("scrolled");
});
fetch("/api/data")
  .then(response => response.json())
  .then(data => {
    console.log(data);
  })
  .catch(error => console.error("HTTP Error:", error));
"#;
        let blocks = generate_js_blocks(js, real_blocks_json);
        let try_blk = blocks
            .iter()
            .find(|b| b.op_code == "jsTryCatch")
            .expect("jsTryCatch from blocks.json");
        assert_eq!(try_blk.block_type, "e");
        assert_eq!(try_blk.parameters, vec!["err"]);
        assert!(try_blk.sub_stack1 >= 0);
        assert!(try_blk.sub_stack2 >= 0);
        assert!(try_blk.is_connected_to_root);
        assert!(try_blk.next_block >= 0);

        let if_blk = blocks.iter().find(|b| b.op_code == "jsIf").expect("jsIf");
        assert!(if_blk.parameters[0].starts_with('@'), "complex condition stacks opAnd: {:?}", if_blk.parameters);
        assert!(blocks.iter().any(|b| b.op_code == "opAnd"));
        assert!(blocks.iter().any(|b| b.op_code == "opGreater"));
        assert!(blocks.iter().any(|b| b.op_code == "opNot"));

        let foreach_blk = blocks.iter().find(|b| b.op_code == "jsForEach").expect("jsForEach");
        assert_eq!(foreach_blk.parameters, vec!["items", "item"]);
        assert!(foreach_blk.sub_stack1 >= 0);

        assert!(blocks.iter().any(|b| b.op_code == "asdN" && b.parameters[0].contains("customUnhandledHelper")));
        assert!(blocks.iter().any(|b| b.op_code == "jsOnScroll" && b.sub_stack1 >= 0));
        let fetch_blk = blocks.iter().find(|b| b.op_code == "httpFetchGet").expect("httpFetchGet");
        assert_eq!(fetch_blk.parameters, vec!["\"/api/data\""]);
        assert!(fetch_blk.sub_stack1 >= 0);
    }
}
