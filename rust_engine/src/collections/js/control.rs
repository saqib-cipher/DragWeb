use crate::models::{make_bean, BlockPalette};

pub struct Control;

impl Control {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Control", -28416); // 0xFFFF9100

        palette.push(make_bean("Branching", "jsIf", "if (%b) {\n", "c", -28416, "if (%1$s) {\n%2$s\n}\n", vec!["true"]));
        palette.push(make_bean("", "jsIfElse", "if (%b) \n", "e", -28416, "if (%1$s) {\n%2$s\n} else {\n%3$s\n}\n", vec!["true"]));
        palette.push(make_bean("Loops", "jsWhile", "while (%b) {\n", "c", -28416, "while (%1$s) {\n%2$s\n}\n", vec!["true"]));
        palette.push(make_bean("", "jsForLoop", "for (let i = 0; i < %d; i++) {\n", "c", -28416, "for (let i = 0; i < %1$s; i++) {\n%2$s\n}\n", vec!["10"]));
        palette.push(make_bean("", "jsForEach", "%s.inputOnly .forEach %s.inputOnly =>", "c", -28416, "%1$s.forEach(%2$s => {\n%3$s\n});\n", vec!["items", "item"]));

        palette
    }
}

