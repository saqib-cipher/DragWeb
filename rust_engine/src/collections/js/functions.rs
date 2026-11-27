use crate::models::{make_bean, BlockPalette};

pub struct Functions;

impl Functions {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Functions", -8708190); // 0xFF7B1FA2

        palette.push(make_bean("Console", "jsConsoleLog", "console.log %s", " ", -8708190, "console.log(%1$s);\n", vec!["\"Debug\""]));
        palette.push(make_bean("Dialog", "jsAlert", "alert %s", " ", -8708190, "alert(%1$s);\n", vec!["\"Alert!\""]));

        palette
    }
}

