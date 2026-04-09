import re

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'r') as f:
    content = f.read()

# Fix Undo logic. It was saving state but first state was not saved, and undo requires at least 2 states. Let's make sure saveUndoState works perfectly.

old_save_undo_state = """    private void saveUndoState() {
        undoStack.add(logicBlockManager.toJson());
        if (undoStack.size() > MAX_UNDO) undoStack.remove(0);
        redoStack.clear();
    }"""

new_save_undo_state = """    private void saveUndoState() {
        // Only save if it's different from the last state
        String currentState = logicBlockManager.toJson();
        if (undoStack.isEmpty() || !undoStack.get(undoStack.size() - 1).equals(currentState)) {
            undoStack.add(currentState);
            if (undoStack.size() > MAX_UNDO) undoStack.remove(0);
        }
        redoStack.clear();
    }"""

old_undo = """    private void undo() {
        if (undoStack.size() <= 1) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        redoStack.add(logicBlockManager.toJson());
        undoStack.remove(undoStack.size() - 1);
        logicBlockManager.fromJson(undoStack.get(undoStack.size() - 1));
        refreshWorkspace();
    }"""

new_undo = """    private void undo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentState = logicBlockManager.toJson();
        // If current state matches the top of stack, we pop it first
        if (undoStack.size() > 1 && undoStack.get(undoStack.size() - 1).equals(currentState)) {
            undoStack.remove(undoStack.size() - 1);
        }

        redoStack.add(currentState);
        String previousState = undoStack.remove(undoStack.size() - 1);
        logicBlockManager.fromJson(previousState);
        refreshWorkspace();
    }"""

content = content.replace(old_save_undo_state, new_save_undo_state)
content = content.replace(old_undo, new_undo)

with open('source/app/src/main/java/sketchweb/gl/LogicBlockActivity.java', 'w') as f:
    f.write(content)
