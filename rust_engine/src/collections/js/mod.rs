pub mod control;
pub mod direct_js;
pub mod dom_elements;
pub mod events;
pub mod functions;
pub mod operators;
pub mod variables;

use crate::models::BlockPalette;

pub fn load() -> Vec<BlockPalette> {
    vec![
        variables::Variables::load(),
        control::Control::load(),
        operators::Operators::load(),
        dom_elements::DomElements::load(),
        events::Events::load(),
        functions::Functions::load(),
        direct_js::DirectJs::load(),
    ]
}

