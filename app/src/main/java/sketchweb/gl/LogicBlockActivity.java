	private void saveAndFinish() {
		try {
				saveLogic();
				DesignDataManager.setBlocks(this.pageName, this.id + LOGIC_NAME_SEPARATOR + this.eventName, this.pane.getBlocks());
				DesignDataManager.saveSavedLogic(this.context != null ? this.context : this, this.projectId, this.pageName);
				ProjectCodeGenerator.generateAndSaveAssets(this.context != null ? this.context : this, this.projectId, this.pageName);
		} catch (Exception e) {
				e.printStackTrace();
		}
		setResult(RESULT_OK);
	}

	@Override
	protected void onPause() {
		super.onPause();
		try {
				saveLogic();
				DesignDataManager.setBlocks(this.pageName, this.id + LOGIC_NAME_SEPARATOR + this.eventName, this.pane.getBlocks());
				DesignDataManager.saveSavedLogic(this.context != null ? this.context : this, this.projectId, this.pageName);
				ProjectCodeGenerator.generateAndSaveAssets(this.context != null ? this.context : this, this.projectId, this.pageName);
		} catch (Exception e) {
				e.printStackTrace();
		}
	}