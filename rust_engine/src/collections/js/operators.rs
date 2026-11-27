use crate::models::{make_bean, BlockPalette};

pub struct Operators;

impl Operators {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Operators", -16718218); // 0xFF00E676

        palette.push(make_bean("Math", "op_add", "%s + %s", "s", -16718218, "(%1$s + %2$s)", vec!["", ""]));
        palette.push(make_bean("", "op_sub", "%s - %s", "s", -16718218, "(%1$s - %2$s)", vec!["", ""]));
        palette.push(make_bean("", "op_mul", "%s * %s", "s", -16718218, "(%1$s * %2$s)", vec!["", ""]));
        palette.push(make_bean("", "op_div", "%s / %s", "s", -16718218, "(%1$s / %2$s)", vec!["", ""]));
        palette.push(make_bean("Logic", "op_equals", "%s === %s", "b", -16718218, "(%1$s === %2$s)", vec!["", ""]));
        palette.push(make_bean("", "op_and", "%b && %b", "b", -16718218, "(%1$s && %2$s)", vec!["true", "true"]));
        palette.push(make_bean("", "op_or", "%b || %b", "b", -16718218, "(%1$s || %2$s)", vec!["true", "false"]));
        palette.push(make_bean("", "op_not", "! %b", "b", -16718218, "(!%1$s)", vec!["true"]));

        palette
    }
}

