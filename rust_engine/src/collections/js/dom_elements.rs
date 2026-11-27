use crate::models::{make_bean, BlockPalette};

pub struct DomElements;

impl DomElements {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("DOM Elements", -1086464); // 0xFFEF6C00

        palette.push(make_bean("Selectors", "jsGetById", "getElementById %id.selector", "s", -1086464, "document.getElementById(\"%1$s\")", vec!["btn"]));
        palette.push(make_bean("", "jsQuerySelector", "querySelector %selector", "s", -1086464, "document.querySelector(\"%1$s\")", vec![".card"]));
        palette.push(make_bean("Content", "jsSetInnerHTML", "set innerHTML of element %s.inputOnly to %s", " ", -1086464, "%1$s.innerHTML = %2$s;\n", vec!["el", "\"Hello\""]));
        palette.push(make_bean("", "jsSetTextContent", "set textContent of element %s.inputOnly to %s", " ", -1086464, "%1$s.textContent = %2$s;\n", vec!["el", "\"Hello\""]));
        palette.push(make_bean("Style", "jsSetStyle", "on element %s.inputOnly set style %s.inputOnly to %s", " ", -1086464, "%1$s.style.%2$s = %3$s;\n", vec!["el", "color", "\"red\""]));

        palette
    }
}

