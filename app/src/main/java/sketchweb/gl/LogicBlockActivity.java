package sketchweb.gl;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.widget.Toolbar;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import androidx.appcompat.app.AppCompatActivity;
import android.animation.Animator;
import android.content.Context;
import java.util.Arrays;
import android.text.TextWatcher;
import android.text.Editable;
import android.content.DialogInterface;

public class LogicBlockActivity extends AppCompatActivity implements OnClickListener, OnBlockCategorySelectListener, OnTouchListener {
		public static final String LOGIC_NAME_SEPARATOR = "_";
		private static final int PALETTE_SIZE_HORIZONTAL = 320;
		private static final int PALETTE_SIZE_VERTICAL = 240;
		public static String filename = "";
		public static String projectId = "";
		public static String pageName = "";

		public static boolean isCssEvent() {
				return filename != null && (filename.toLowerCase().endsWith(".css") || filename.toLowerCase().contains("css"));
		}
		
		private Context context;
		
		private ObjectAnimator aniHideIconDelete;
		private ObjectAnimator aniHidePalette;
		private ObjectAnimator aniShowIconDelete;
		private ObjectAnimator aniShowPalette;
		private LinearLayout areaPalette;
		private boolean bActiveIconDelete = false;
		private boolean bInitIconDeleteAnimation = false;
		private boolean bInitPaletteAnimation = false;
		private boolean bShowIconDelete = false;
		private BlockCopyInterface blockCopyInterface;
		private VariableNameValidator booleanValidator;
		private View currentTouchedView = null;
		private ViewDummy dummy;
		private ViewLogicEditor editor;
		private String eventName = "";
		private FloatingActionButton fab;
		private final Handler handler = new Handler();
		private ImageView iconDelete;
		private LinearLayout layoutDragActions;
		private ImageView iconSave;
		private ImageView iconDuplicate;
		private boolean bActiveIconSave = false;
		private boolean bActiveIconDuplicate = false;
		private String id = "";
		private boolean isDragged = false;
		private boolean isPaletteOpened = false;
		private LinearLayout layoutPalette;
		private Runnable longPressed = new Runnable() {
				@Override
				public void run() {
						dragStart();
				}
		};
		private AlertDialog mDlg;
		private Menu menu;
		private int minDist = 0;
		private VariableNameValidator numberValidator;
		private int originalArgIndex;
		private int originalInsertOption;
		private Block originalParent;
		private PaletteBlock paletteBlock;
		private PaletteSelector paletteSelector;
		private BlockPane pane;
		private int[] posDummy = new int[2];
		private float posInitX = 0.0f;
		private float posInitY = 0.0f;
		private int[] posOriginal = new int[2];
		private SharedPreferenceUtil prefBackup;
		private SharedPreferenceUtil prefInstall;
		private VariableNameValidator stringValidator;
		private Toolbar toolbar;
		private boolean useVibrate;
		private Vibrator vibrator;
		private Map<String, ArrayList<BlockBean>> collectionBlocksMap = new HashMap<>();
		private PaletteSelector.CategoryItem currentCategoryItem;
		private final ArrayList<String> undoStack = new ArrayList<>();
		private final ArrayList<String> redoStack = new ArrayList<>();
		private boolean isUndoRedoAction = false;
		
		//new
		public int BLOCK_DRAG_X = 0;
		public int BLOCK_DRAG_Y = -30;
		
		private void activeIconDelete(boolean z) {
				if (this.bActiveIconDelete != z) {
						this.bActiveIconDelete = z;
						if (this.bActiveIconDelete) {
								this.iconDelete.setImageResource(R.drawable.trash_act);
						} else {
								this.iconDelete.setImageResource(R.drawable.trash);
						}
				}
		}

		private void activeIconSave(boolean z) {
				if (this.bActiveIconSave != z) {
						this.bActiveIconSave = z;
						if (this.iconSave != null) {
								if (this.bActiveIconSave) {
									this.iconSave.setImageResource(R.drawable.device_floppy_act);
								} else {
									this.iconSave.setImageResource(R.drawable.device_floppy);
								}
						}
				}
		}

		private void activeIconDuplicate(boolean z) {
				if (this.bActiveIconDuplicate != z) {
						this.bActiveIconDuplicate = z;
						if (this.iconDuplicate != null) {
								if (this.bActiveIconDuplicate) {
									this.iconDuplicate.setImageResource(R.drawable.copy_act);
								} else {
									this.iconDuplicate.setImageResource(R.drawable.copy);
								}
						}
				}
		}
		
		private void addBlockToPalette(String str, String str2, String str3, int i, Object... objArr) {
				BlockBase addBlock = this.paletteBlock.addBlock(str, str2, str3, i, objArr);
				addBlock.setClickable(true);
				addBlock.setOnTouchListener(this);
		}
		
		private void addButtonToPalette(String str, String str2) {
				View addButton = this.paletteBlock.addButton(str, str2);
				addButton.setTag(str2);
				addButton.setSoundEffectsEnabled(true);
				addButton.setOnClickListener(this);
		}
		
		private void addFunctions() {
				Iterator it = DesignDataManager.getFunctions(filename).iterator();
				while (it.hasNext()) {
						addBlockToPalette((String) ((Pair) it.next()).second, " ", "definedFunc", -7711273, new Object[0]);
				}
		}
		
		private void addLists() {
				Iterator it = DesignDataManager.getLists(filename).iterator();
				int i = 0;
				int i2 = 0;
				while (it.hasNext()) {
						if (((Integer) ((Pair) it.next()).first).intValue() == 1) {
								i2++;
						} else {
								i++;
						}
				}
				if (i2 > 0) {
						ArrayList arrayList = new ArrayList();
						addBlockToPalette("", " ", "addListInt", -3384542, new Object[0]);
						addBlockToPalette("", " ", "insertListInt", -3384542, new Object[0]);
						addBlockToPalette("", "d", "getAtListInt", -3384542, new Object[0]);
						addBlockToPalette("", "d", "indexListInt", -3384542, new Object[0]);
						addBlockToPalette("", "b", "containListInt", -3384542, new Object[0]);
				}
				if (i > 0) {
						addBlockToPalette("", " ", "addListStr", -3384542, new Object[0]);
						addBlockToPalette("", " ", "insertListStr", -3384542, new Object[0]);
						addBlockToPalette("", "s", "getAtListStr", -3384542, new Object[0]);
						addBlockToPalette("", "d", "indexListStr", -3384542, new Object[0]);
						addBlockToPalette("", "b", "containListStr", -3384542, new Object[0]);
				}
				if (i2 > 0 || i > 0) {
						addBlockToPalette("", " ", "deleteList", -3384542, new Object[0]);
						addBlockToPalette("", "d", "lengthList", -3384542, new Object[0]);
						addBlockToPalette("", " ", "clearList", -3384542, new Object[0]);
				}
		}
		
		private void addVariables() {
				ArrayList<Pair<Integer, String>> vars = DesignDataManager.getVariables(filename);
				
				ArrayList<String> bools = new ArrayList<>();
				ArrayList<String> nums = new ArrayList<>();
				ArrayList<String> strs = new ArrayList<>();
				
				for (Pair<Integer, String> pair : vars) {
						int t = pair.first;
						if (t == 0 || t == 4) {
								bools.add(pair.second);
						} else if (t == 1 || t == 5) {
								nums.add(pair.second);
						} else if (t == 2 || t == 6) {
								strs.add(pair.second);
						}
				}
				
				if (!bools.isEmpty()) {
						addButtonToPalette("Boolean", "label_bool");
						for (String b : bools) {
								addBlockToPalette(b, "b", "getVar", -1147626, new Object[0]);
						}
				}
				
				if (!nums.isEmpty()) {
						addButtonToPalette("Number", "label_num");
						for (String n : nums) {
								addBlockToPalette(n, "d", "getVar", -1147626, new Object[0]);
						}
				}
				
				if (!strs.isEmpty()) {
						addButtonToPalette("String", "label_str");
						for (String s : strs) {
								addBlockToPalette(s, "s", "getVar", -1147626, new Object[0]);
						}
				}
				
				if (!bools.isEmpty() || !nums.isEmpty() || !strs.isEmpty()) {
						addButtonToPalette("Blocks", "label_blocks");
				}
				
				if (!bools.isEmpty()) {
						addBlockToPalette("", " ", "setVarBoolean", -1147626, new Object[0]);
				}
				if (!nums.isEmpty()) {
						addBlockToPalette("", " ", "setVarInt", -1147626, new Object[0]);
						addBlockToPalette("", " ", "increaseInt", -1147626, new Object[0]);
						addBlockToPalette("", " ", "decreaseInt", -1147626, new Object[0]);
				}
				if (!strs.isEmpty()) {
						addBlockToPalette("", " ", "setVarString", -1147626, new Object[0]);
				}
		}
		
		private void allocateBlockArea(int i) {
		}
		
		private void allocatePalette(int var1) {
				if(2 == var1) {
						LayoutParams var2 = new LayoutParams((int)LayoutUtil.getDip(this, 320.0F), -1);
						this.areaPalette.setLayoutParams(var2);
						LayoutParams var3 = new LayoutParams(-2, -2);
						var3.gravity = 81;
						int var4 = (int)LayoutUtil.getDip(this, 8.0F);
						var3.setMargins(var4, var4, var4, var4);
						this.fab.setLayoutParams(var3);
						android.widget.RelativeLayout.LayoutParams var5 = new android.widget.RelativeLayout.LayoutParams(-2, -1);
						var5.addRule(10);
						var5.addRule(11);
						var5.topMargin = this.getSupportActionBar().getHeight();
						this.layoutPalette.setOrientation(0);
						this.layoutPalette.setLayoutParams(var5);
				} else {
						LayoutParams var6 = new LayoutParams(-1, (int)LayoutUtil.getDip(this, 240.0F));
						this.areaPalette.setLayoutParams(var6);
						LayoutParams var7 = new LayoutParams(-2, -2);
						var7.gravity = 21;
						int var8 = (int)LayoutUtil.getDip(this, 8.0F);
						var7.setMargins(var8, var8, var8, var8);
						this.fab.setLayoutParams(var7);
						android.widget.RelativeLayout.LayoutParams var9 = new android.widget.RelativeLayout.LayoutParams(-1, -2);
						var9.addRule(9);
						var9.addRule(12);
						this.layoutPalette.setOrientation(1);
						this.layoutPalette.setLayoutParams(var9);
				}
				
				this.initPaletteAnimation(var1);
				this.allocateBlockArea(var1);
		}
		
		private void backupCurrentData(Bundle bundle) {
		}
		
		private void cancelIconDeleteAnimation() {
				if (this.aniShowIconDelete.isRunning()) {
						this.aniShowIconDelete.cancel();
				}
				if (this.aniHideIconDelete.isRunning()) {
						this.aniHideIconDelete.cancel();
				}
		}
		
		private void cancelPaletteAnimation() {
				if (this.aniShowPalette.isRunning()) {
						this.aniShowPalette.cancel();
				}
				if (this.aniHidePalette.isRunning()) {
						this.aniHidePalette.cancel();
				}
		}
		
		private RadioButton createSingleItem(String str) {
				RadioButton radioButton = new RadioButton(this);
				radioButton.setText(str);
				ViewGroup.LayoutParams layoutParams = new LayoutParams(-1, (int) (40.0f * LayoutUtil.getDip(this.context, 1.0f)));
				radioButton.setGravity(19);
				radioButton.setLayoutParams(layoutParams);
				return radioButton;
		}
		
		private void dragStart() {
				if (this.currentTouchedView != null) {
						this.paletteBlock.setDragEnabled(false);
						this.editor.setScrollEnabled(false);
						if (this.useVibrate) {
								this.vibrator.vibrate(100);
						}
						this.isDragged = true;
						this.dummy.setDummyVisibility(View.VISIBLE);
						this.dummy.bringToFront();
						if (((Block) this.currentTouchedView).getBlockType() == 0) {
								getOriginalState((Block) this.currentTouchedView);
								if (this.iconSave != null) { this.iconSave.setVisibility(View.VISIBLE); this.iconSave.setAlpha(1.0f); }
								if (this.iconDuplicate != null) { this.iconDuplicate.setVisibility(View.VISIBLE); this.iconDuplicate.setAlpha(1.0f); }
								showIconDelete(true);
								this.dummy.makeDummyWithBlock((Block) this.currentTouchedView);
								this.pane.setVisibleBlock((Block) this.currentTouchedView, 8);
								this.pane.removeRelation((Block) this.currentTouchedView);
						} else {
								Block b = (Block) this.currentTouchedView;
								if (b != null && b.mOpCode != null && collectionBlocksMap.containsKey(b.mOpCode)) {
										if (this.iconSave != null) { this.iconSave.setVisibility(View.GONE); }
										if (this.iconDuplicate != null) { this.iconDuplicate.setVisibility(View.GONE); }
										showIconDelete(true);
								} else {
										if (this.iconSave != null) { this.iconSave.setVisibility(View.VISIBLE); this.iconSave.setAlpha(1.0f); }
										if (this.iconDuplicate != null) { this.iconDuplicate.setVisibility(View.VISIBLE); this.iconDuplicate.setAlpha(1.0f); }
								}
								this.dummy.makeDummyWithBlock((Block) this.currentTouchedView);
						}
						this.pane.prepareToDrag((Block) this.currentTouchedView);
						this.dummy.moveDummy(this.currentTouchedView, this.posInitX, this.posInitY, this.posInitX, this.posInitY, (float)BLOCK_DRAG_X, (float)BLOCK_DRAG_Y);
						this.dummy.getDummyPosition(this.posDummy);
						if (this.editor.hitTest((float) this.posDummy[0], (float) this.posDummy[1])) {
								this.dummy.setAllow(true);
								this.pane.updateFeedbackFor((Block) this.currentTouchedView, this.posDummy[0], this.posDummy[1]);
								return;
						}
						this.dummy.setAllow(false);
						this.pane.hideFeedbackShape();
				}
		}
		
		private int getLabelWidth(TextView textView) {
				Rect rect = new Rect();
				textView.getPaint().getTextBounds(textView.getText().toString(), 0, textView.getText().length(), rect);
				return rect.width();
		}
		
		private void getOriginalState(Block block) {
				this.originalParent = null;
				this.originalArgIndex = -1;
				this.originalInsertOption = 0;
				this.posOriginal = new int[2];
				block.getLocationOnScreen(this.posOriginal);
				if (block.parentBlock != null) {
						this.originalParent = block.parentBlock;
				}
				if (this.originalParent != null) {
						if (this.originalParent.nextBlock == ((Integer) block.getTag()).intValue()) {
								this.originalInsertOption = 0;
						} else if (this.originalParent.subStack1 == ((Integer) block.getTag()).intValue()) {
								this.originalInsertOption = 2;
						} else if (this.originalParent.subStack2 == ((Integer) block.getTag()).intValue()) {
								this.originalInsertOption = 3;
						} else if (this.originalParent.args.contains(block)) {
								this.originalInsertOption = 5;
								this.originalArgIndex = this.originalParent.args.indexOf(block);
						}
				}
		}
		
		private boolean hitTestIcon(View view, float f, float f2) {
				if (view == null || view.getVisibility() != View.VISIBLE) return false;
				int[] iArr = new int[2];
				view.getLocationOnScreen(iArr);
				return f > ((float) iArr[0]) && f < ((float) (iArr[0] + view.getWidth())) && f2 > ((float) iArr[1]) && f2 < ((float) (iArr[1] + view.getHeight()));
		}

		private boolean hitTestIconDelete(float f, float f2) {
				return hitTestIcon(this.iconDelete, f, f2);
		}
		
		private void initIconDeleteAnimation() {
				if (this.layoutDragActions == null) return;
				this.aniShowIconDelete = ObjectAnimator.ofFloat(this.layoutDragActions, "TranslationY", new float[]{0.0f});
				this.aniShowIconDelete.setDuration(500);
				this.aniShowIconDelete.setInterpolator(new DecelerateInterpolator());
				this.aniHideIconDelete = ObjectAnimator.ofFloat(this.layoutDragActions, "TranslationY", new float[]{(float) LayoutUtil.getDip(this, 80.0f)});
				this.aniHideIconDelete.setDuration(300);
				this.aniHideIconDelete.setInterpolator(new DecelerateInterpolator());
				this.bInitIconDeleteAnimation = true;
		}
		
		private void initPaletteAnimation(int i) {
				if (2 == i) {
						if (this.isPaletteOpened) {
								this.layoutPalette.setTranslationX(0.0f);
								this.layoutPalette.setTranslationY(0.0f);
						} else {
								this.layoutPalette.setTranslationX((float) ((int) LayoutUtil.getDip(this, 320.0f)));
								this.layoutPalette.setTranslationY(0.0f);
						}
				} else if (this.isPaletteOpened) {
						this.layoutPalette.setTranslationX(0.0f);
						this.layoutPalette.setTranslationY(0.0f);
				} else {
						this.layoutPalette.setTranslationX(0.0f);
						this.layoutPalette.setTranslationY((float) ((int) LayoutUtil.getDip(this, 240.0f)));
				}
				if (2 == i) {
						this.aniShowPalette = ObjectAnimator.ofFloat(this.layoutPalette, "TranslationX", new float[]{0.0f});
						this.aniHidePalette = ObjectAnimator.ofFloat(this.layoutPalette, "TranslationX", new float[]{(float) ((int) LayoutUtil.getDip(this, 320.0f))});
				} else {
						this.aniShowPalette = ObjectAnimator.ofFloat(this.layoutPalette, "TranslationY", new float[]{0.0f});
						this.aniHidePalette = ObjectAnimator.ofFloat(this.layoutPalette, "TranslationY", new float[]{(float) ((int) LayoutUtil.getDip(this, 240.0f))});
				}
				this.aniShowPalette.removeAllListeners();
				this.aniHidePalette.removeAllListeners();
				this.aniShowPalette.addListener(new Animator.AnimatorListener() {
						public void onAnimationCancel(Animator var1) {
						}
						
						public void onAnimationEnd(Animator var1) {
								updateIconDeletePosition();
						}
						
						public void onAnimationRepeat(Animator var1) {
						}
						
						public void onAnimationStart(Animator var1) {
						}
				});
				this.aniHidePalette.addListener(new Animator.AnimatorListener() {
						public void onAnimationCancel(Animator var1) {
						}
						
						public void onAnimationEnd(Animator var1) {
						}
						
						public void onAnimationRepeat(Animator var1) {
						}
						
						public void onAnimationStart(Animator var1) {
								updateIconDeletePosition();
						}
				});
				this.aniShowPalette.setDuration(500);
				this.aniShowPalette.setInterpolator(new DecelerateInterpolator());
				this.aniHidePalette.setDuration(300);
				this.aniHidePalette.setInterpolator(new DecelerateInterpolator());
				this.bInitPaletteAnimation = true;
		}
		
		private void loadLogic() {
				Map hashMap = new HashMap();
				DesignDataManager.initialize(this.context, this.projectId, this.pageName);
				String eventKey = this.id + LOGIC_NAME_SEPARATOR + this.eventName;
				ArrayList blocks = DesignDataManager.getBlocks(filename, eventKey);
				if (blocks == null || blocks.isEmpty()) {
						if ("initializeLogic".equals(this.eventName)) {
								blocks = DesignDataManager.getBlocks(filename, "onCreate_initializeLogic");
						}
				}
				if (blocks != null) {
						java.util.Collections.sort(blocks, (b1, b2) -> {
								BlockBean bean1 = (BlockBean) b1;
								BlockBean bean2 = (BlockBean) b2;
								return Integer.compare(bean1.stackIndex, bean2.stackIndex);
						});

						Iterator it = blocks.iterator();
						int i = 1;
						while (it.hasNext()) {
								Block makeBlockFromBean = makeBlockFromBean((BlockBean) it.next());
								hashMap.put(Integer.valueOf(((Integer) makeBlockFromBean.getTag()).intValue()), makeBlockFromBean);
								this.pane.blockId = Math.max(this.pane.blockId, ((Integer) makeBlockFromBean.getTag()).intValue() + 1);
								this.pane.addBlock(makeBlockFromBean, 0, 0);
								makeBlockFromBean.setOnTouchListener(this);
								if (i != 0) {
										this.pane.getRoot().insertBlock(makeBlockFromBean);
										i = 0;
								}
						}
						Iterator it2 = blocks.iterator();
						while (it2.hasNext()) {
								BlockBean blockBean = (BlockBean) it2.next();
								Block block = (Block) hashMap.get(Integer.valueOf(blockBean.id));
								if (block != null) {
										Block block2;
										if (blockBean.subStack1 >= 0) {
												block2 = (Block) hashMap.get(Integer.valueOf(blockBean.subStack1));
												if (block2 != null) {
														block.insertBlockSub1(block2);
												}
										}
										if (blockBean.subStack2 >= 0) {
												block2 = (Block) hashMap.get(Integer.valueOf(blockBean.subStack2));
												if (block2 != null) {
														block.insertBlockSub2(block2);
												}
										}
										if (blockBean.nextBlock >= 0) {
												block2 = (Block) hashMap.get(Integer.valueOf(blockBean.nextBlock));
												if (block2 != null) {
														block.insertBlock(block2);
												}
										}
										int size = blockBean.parameters.size();
										for (int i2 = 0; i2 < size; i2++) {
												String str = (String) blockBean.parameters.get(i2);
												if (str != null && str.length() > 0) {
														if (str.charAt(0) == '@') {
																block2 = (Block) hashMap.get(Integer.valueOf(Integer.valueOf(str.substring(1)).intValue()));
																if (block2 != null) {
																		block.replaceArgWithBlock((BlockBase) block.args.get(i2), block2);
																}
														} else {
																((BlockArg) block.args.get(i2)).setArgValue(str);
																block.recalcWidthToParent();
														}
												}
										}
								}
						}
						this.pane.getRoot().fixLayout();
						this.pane.calculateWidthHeight();
				}
		}
		
		private void makeBlockWithSpec(ViewGroup viewGroup, ViewGroup viewGroup2, Block block, String str, ArrayList<Pair<String, String>> arrayList) {
				int i;
				int i2;
				viewGroup.removeAllViews();
				viewGroup.addView(block);
				Iterator it = arrayList.iterator();
				String str2 = str;
				while (it.hasNext()) {
						Pair pair = (Pair) it.next();
						str2 = ((String) pair.first).equals("b") ? str2 + " %b." + ((String) pair.second) : ((String) pair.first).equals("d") ? str2 + " %d." + ((String) pair.second) : ((String) pair.first).equals("s") ? str2 + " %s." + ((String) pair.second) : str2 + " " + ((String) pair.second);
				}
				block.setSpec(str2, null);
				int size = arrayList.size();
				int i3 = 0;
				for (i = 0; i < size; i++) {
						Pair pair = (Pair) arrayList.get(i);
						Block block2;
						int i4;
						if (((String) pair.first).equals("b")) {
								block2 = new Block(getApplicationContext(), arrayList.indexOf(pair) + 1, (String) pair.second, "b", "getParam", new Object[]{Integer.valueOf(-7711273), ""});
								viewGroup.addView(block2);
								i4 = i3 + 1;
								block.replaceArgWithBlock((BlockBase) block.args.get(i3), block2);
								i2 = i4;
						} else if (((String) pair.first).equals("d")) {
								block2 = new Block(getApplicationContext(), arrayList.indexOf(pair) + 1, (String) pair.second, "d", "getParam", new Object[]{Integer.valueOf(-7711273), ""});
								viewGroup.addView(block2);
								i4 = i3 + 1;
								block.replaceArgWithBlock((BlockBase) block.args.get(i3), block2);
								i2 = i4;
						} else if (((String) pair.first).equals("s")) {
								block2 = new Block(getApplicationContext(), arrayList.indexOf(pair) + 1, (String) pair.second, "s", "getParam", new Object[]{Integer.valueOf(-7711273), ""});
								viewGroup.addView(block2);
								i4 = i3 + 1;
								block.replaceArgWithBlock((BlockBase) block.args.get(i3), block2);
								i2 = i4;
						} else {
								i2 = i3;
						}
						i3 = i2;
				}
				block.fixLayout();
				viewGroup2.removeAllViews();
				i = block.labelsAndArgs.size();
				for (i3 = 0; i3 < i; i3++) {
						View view = (View) block.labelsAndArgs.get(i3);
						int i5 = 0;
						if (((String) block.argTypes.get(i3)).equals("label")) {
								i5 = getLabelWidth((TextView) view);
						}
						if (view instanceof Block) {
								i5 = ((Block) view).getWidthSum();
						}
						i2 = (int) (((float) i5) + LayoutUtil.getDip(getApplicationContext(), 4.0f));
						ImageView imageView = new ImageView(this);
						imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
						imageView.setScaleType(ScaleType.CENTER_INSIDE);
						imageView.setPadding(0, (int) LayoutUtil.getDip(getApplicationContext(), 4.0f), 0, (int) LayoutUtil.getDip(getApplicationContext(), 4.0f));
						imageView.setLayoutParams(new LayoutParams(i2, -1));
						viewGroup2.addView(imageView);
						if (i3 == 0) {
								imageView.setVisibility(4);
								imageView.setEnabled(false);
						} else {
								imageView.setOnClickListener(new LogicBlockActivity$18(this, arrayList, viewGroup2, viewGroup, block, str));
						}
				}
		}
		
		
		
		class LogicBlockActivity$18 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final ArrayList val$args;
				// $FF: synthetic field
				final Block val$b;
				// $FF: synthetic field
				final ViewGroup val$removeArea;
				// $FF: synthetic field
				final String val$spec;
				// $FF: synthetic field
				final ViewGroup val$v;
				
				LogicBlockActivity$18(LogicBlockActivity var1, ArrayList var2, ViewGroup var3, ViewGroup var4, Block var5, String var6) {
						this.this$0 = var1;
						this.val$args = var2;
						this.val$removeArea = var3;
						this.val$v = var4;
						this.val$b = var5;
						this.val$spec = var6;
				}
				
				public void onClick(View var1) {
						this.val$args.remove(-1 + this.val$removeArea.indexOfChild(var1));
						ArrayList var3 = new ArrayList(Arrays.asList(new String[0]/*DefineSource.getUsedWord(DesignActivity.getScId())*/));
						Iterator var4 = this.val$args.iterator();
						
						while(var4.hasNext()) {
								Pair var5 = (Pair)var4.next();
								if(!((String)var5.first).equals("t")) {
										var3.add(var5.second);
								}
						}
						
						booleanValidator.setUsedWords((String[])var3.toArray(new String[var3.size()]));
						numberValidator.setUsedWords((String[])var3.toArray(new String[var3.size()]));
						stringValidator.setUsedWords((String[])var3.toArray(new String[var3.size()]));
						makeBlockWithSpec(this.val$v, this.val$removeArea, this.val$b, this.val$spec, this.val$args);
				}
		}
		
		
		
		
		private void openPalette(boolean z) {
				if (!this.bInitPaletteAnimation) {
						initPaletteAnimation(getResources().getConfiguration().orientation);
				}
				if (this.isPaletteOpened != z) {
						this.isPaletteOpened = z;
						cancelPaletteAnimation();
						if (z) {
								this.aniShowPalette.start();
						} else {
								this.aniHidePalette.start();
						}
						allocateBlockArea(getResources().getConfiguration().orientation);
				}
		}
		
		private int safeParseInt(String s) {
				if (s == null) return 0;
				try {
						return Integer.parseInt(s.trim());
				} catch (Exception e) {
						int h = s.hashCode();
						return h == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(h) % 10000000;
				}
		}

		private void pasteCopiedBlocks() {
				if (DesignDataManager.isExistClipboard(filename)) {
						int i;
						BlockBean blockBean;
						int i2;
						int i3;
						Block makeBlockFromBean;
						Map hashMap = new HashMap();
						Map hashMap2 = new HashMap();
						ArrayList clipboard = DesignDataManager.getClipboard(filename);
						Iterator it = clipboard.iterator();
						while (it.hasNext()) {
								Integer valueOf = Integer.valueOf(safeParseInt(((BlockBean) it.next()).id));
								BlockPane blockPane = this.pane;
								i = blockPane.blockId;
								blockPane.blockId = i + 1;
								hashMap2.put(valueOf, Integer.valueOf(i));
						}
						Iterator it2 = clipboard.iterator();
						while (it2.hasNext()) {
								blockBean = (BlockBean) it2.next();
								if (blockBean.opCode.equals("getArg")) {
										i = 0;
										i2 = 0;
										while (i < this.pane.getRoot().args.size()) {
												View view = (View) this.pane.getRoot().args.get(i);
												i3 = ((view instanceof Block) && blockBean.type.equals(((Block) view).mType) && blockBean.spec.equals(((Block) view).mSpec)) ? 1 : i2;
												i++;
												i2 = i3;
										}
										if (i2 == 0) {
												hashMap2.put(Integer.valueOf(safeParseInt(blockBean.id)), Integer.valueOf(0));
										}
								}
						}
						Iterator it3 = clipboard.iterator();
						while (it3.hasNext()) {
								blockBean = (BlockBean) it3.next();
								Integer mappedId = (Integer) hashMap2.get(Integer.valueOf(safeParseInt(blockBean.id)));
								blockBean.id = String.valueOf(mappedId != null ? mappedId.intValue() : 0);
								i2 = blockBean.parameters.size();
								for (i3 = 0; i3 < i2; i3++) {
										String str = (String) blockBean.parameters.get(i3);
										if (str != null && str.length() > 0 && str.charAt(0) == '@') {
												Integer num = (Integer) hashMap2.get(Integer.valueOf(safeParseInt(str.substring(1))));
												if (num == null) {
														blockBean.parameters.set(i3, "");
												} else {
														blockBean.parameters.set(i3, '@' + String.valueOf(num));
												}
										}
								}
								if (blockBean.subStack1 >= 0) {
										Integer num = (Integer) hashMap2.get(Integer.valueOf(blockBean.subStack1));
										blockBean.subStack1 = num != null ? num.intValue() : -1;
								}
								if (blockBean.subStack2 >= 0) {
										Integer num = (Integer) hashMap2.get(Integer.valueOf(blockBean.subStack2));
										blockBean.subStack2 = num != null ? num.intValue() : -1;
								}
								if (blockBean.nextBlock >= 0) {
										Integer num = (Integer) hashMap2.get(Integer.valueOf(blockBean.nextBlock));
										blockBean.nextBlock = num != null ? num.intValue() : -1;
								}
						}
						int[] iArr = new int[2];
						this.editor.getLocationOnScreen(iArr);
						int width = iArr[0] + (this.editor.getWidth() / 2) - (int) LayoutUtil.getDip(getApplicationContext(), 40.0f);
						i3 = iArr[1] + (this.editor.getHeight() / 2) - (int) LayoutUtil.getDip(getApplicationContext(), 30.0f);
						it3 = clipboard.iterator();
						Block block = null;
						while (it3.hasNext()) {
								blockBean = (BlockBean) it3.next();
								if (!blockBean.id.equals("0")) {
										makeBlockFromBean = makeBlockFromBean(blockBean);
										hashMap.put(Integer.valueOf(makeBlockFromBean.getTag().toString()), makeBlockFromBean);
										this.pane.addBlock(makeBlockFromBean, width, i3);
										makeBlockFromBean.setOnTouchListener(this);
										block = makeBlockFromBean;
								}
						}
						Iterator it4 = clipboard.iterator();
						while (it4.hasNext()) {
								blockBean = (BlockBean) it4.next();
								if (!blockBean.id.equals("0")) {
										Block block2 = (Block) hashMap.get(Integer.valueOf(blockBean.id));
										if (block2 != null) {
												Block block3;
												int size = blockBean.parameters.size();
												for (int i4 = 0; i4 < size; i4++) {
														String str2 = (String) blockBean.parameters.get(i4);
														if (str2 != null && str2.length() > 0) {
																if (str2.charAt(0) == '@') {
																		block3 = (Block) hashMap.get(Integer.valueOf(Integer.valueOf(str2.substring(1)).intValue()));
																		if (block3 != null) {
																				block2.replaceArgWithBlock((BlockBase) block2.args.get(i4), block3);
																		}
																} else {
																		((BlockArg) block2.args.get(i4)).setArgValue(str2);
																		block2.recalcWidthToParent();
																}
														}
												}
												if (blockBean.subStack1 >= 0) {
														block3 = (Block) hashMap.get(Integer.valueOf(blockBean.subStack1));
														if (block3 != null) {
																block2.insertBlockSub1(block3);
														}
												}
												if (blockBean.subStack2 >= 0) {
														block3 = (Block) hashMap.get(Integer.valueOf(blockBean.subStack2));
														if (block3 != null) {
																block2.insertBlockSub2(block3);
														}
												}
												if (blockBean.nextBlock >= 0) {
														makeBlockFromBean = (Block) hashMap.get(Integer.valueOf(blockBean.nextBlock));
														if (makeBlockFromBean != null) {
																block2.insertBlock(makeBlockFromBean);
														}
												}
										}
								}
						}
						block.topBlock().fixLayout();
						this.pane.calculateWidthHeight();
						return;
				}
				Toast.makeText(this, "No block for copying (for debug)", 0).show();
		}
		
		private void saveLogic() {
				DesignDataManager.setBlocks(filename, this.id + LOGIC_NAME_SEPARATOR + this.eventName, this.pane.getBlocks());
		}
		
		private void showAddBlockPopup() {
				View inflate = LayoutUtil.inflate(this, R.layout.logic_popup_add_block);
				Builder builder = new Builder(this);
				builder.setView(inflate);
				builder.setTitle(getString(R.string.logic_popup_title_make_block));
				ArrayList arrayList = new ArrayList();
				RelativeLayout relativeLayout = (RelativeLayout) inflate.findViewById(R.id.block_area);
				LinearLayout linearLayout = (LinearLayout) inflate.findViewById(R.id.remove_area);
				Block block = new Block(getApplicationContext(), 0, "", " ", "definedFunc", new Object[]{Integer.valueOf(-7711273)});
				relativeLayout.addView(block);
				TextInputLayout textInputLayout = (TextInputLayout) inflate.findViewById(R.id.ti_boolean);
				TextInputLayout textInputLayout2 = (TextInputLayout) inflate.findViewById(R.id.ti_number);
				TextInputLayout textInputLayout3 = (TextInputLayout) inflate.findViewById(R.id.ti_string);
				VariableNameValidator variableNameValidator = new VariableNameValidator(this.context, (TextInputLayout) inflate.findViewById(R.id.ti_name), DefineSource.RESERVED_WORD, DefineSource.getUsedWord(DesignActivity.getScId()), DesignDataManager.getAllNamesForValid(filename));
				this.booleanValidator = new VariableNameValidator(this.context, textInputLayout, DefineSource.RESERVED_WORD, DefineSource.getUsedWord(DesignActivity.getScId()), new ArrayList());
				this.numberValidator = new VariableNameValidator(this.context, textInputLayout2, DefineSource.RESERVED_WORD, DefineSource.getUsedWord(DesignActivity.getScId()), new ArrayList());
				this.stringValidator = new VariableNameValidator(this.context, textInputLayout3, DefineSource.RESERVED_WORD, DefineSource.getUsedWord(DesignActivity.getScId()), new ArrayList());
				EditText editText = (EditText) inflate.findViewById(R.id.ed_name);
				EditText editText2 = (EditText) inflate.findViewById(R.id.ed_boolean);
				EditText editText3 = (EditText) inflate.findViewById(R.id.ed_number);
				EditText editText4 = (EditText) inflate.findViewById(R.id.ed_string);
				EditText editText5 = (EditText) inflate.findViewById(R.id.ed_label);
				editText.setPrivateImeOptions("defaultInputmode=english;");
				editText2.setPrivateImeOptions("defaultInputmode=english;");
				editText3.setPrivateImeOptions("defaultInputmode=english;");
				editText4.setPrivateImeOptions("defaultInputmode=english;");
				editText5.setPrivateImeOptions("defaultInputmode=english;");
				editText.addTextChangedListener(new LogicBlockActivity$12(this, relativeLayout, linearLayout, block, arrayList));
				((Button) inflate.findViewById(R.id.add_boolean)).setOnClickListener(new LogicBlockActivity$13(this, arrayList, editText2, relativeLayout, linearLayout, block, editText, editText3, editText4));
				((Button) inflate.findViewById(R.id.add_number)).setOnClickListener(new LogicBlockActivity$14(this, arrayList, editText3, relativeLayout, linearLayout, block, editText, editText2, editText4));
				((Button) inflate.findViewById(R.id.add_string)).setOnClickListener(new LogicBlockActivity$15(this, arrayList, editText4, relativeLayout, linearLayout, block, editText, editText2, editText3));
				((Button) inflate.findViewById(R.id.add_label)).setOnClickListener(new LogicBlockActivity$16(this, arrayList, editText5, relativeLayout, linearLayout, block, editText));
				builder.setNegativeButton(R.string.btn_cancel, null);
				builder.setPositiveButton(R.string.btn_accept, null);
				this.mDlg = builder.create();
				this.mDlg.setOnShowListener(new LogicBlockActivity$17(this, variableNameValidator, editText, block));
				this.mDlg.show();
		}
		
		
		class LogicBlockActivity$17 implements DialogInterface.OnShowListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final Block val$block;
				// $FF: synthetic field
				final EditText val$edName;
				// $FF: synthetic field
				final VariableNameValidator val$varNameValidator;
				
				LogicBlockActivity$17(LogicBlockActivity var1, VariableNameValidator var2, EditText var3, Block var4) {
						this.this$0 = var1;
						this.val$varNameValidator = var2;
						this.val$edName = var3;
						this.val$block = var4;
				}
				
				public void onShow(DialogInterface var1) {
						LogicBlockActivity.access$800(this.this$0).getButton(-1).setOnClickListener(new LogicBlockActivity$17$1(this));
				}
		}
		
		class LogicBlockActivity$17$1 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity$17 this$1;
				
				LogicBlockActivity$17$1(LogicBlockActivity$17 var1) {
						this.this$1 = var1;
				}
				
				public void onClick(View var1) {
						if(this.this$1.val$varNameValidator.isValid()) {
								DesignDataManager.addFunction(LogicBlockActivity.filename, this.this$1.val$edName.getText().toString(), this.this$1.val$block.mSpec);
								this.this$1.this$0.onBlockCategorySelect(5, -7711273);
								LogicBlockActivity.access$800(this.this$1.this$0).dismiss();
						}
				}
		}
		
		
		
		class LogicBlockActivity$16 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final ArrayList val$args;
				// $FF: synthetic field
				final Block val$block;
				// $FF: synthetic field
				final RelativeLayout val$blockArea;
				// $FF: synthetic field
				final EditText val$edLabel;
				// $FF: synthetic field
				final EditText val$edName;
				// $FF: synthetic field
				final LinearLayout val$removeArea;
				
				LogicBlockActivity$16(LogicBlockActivity var1, ArrayList var2, EditText var3, RelativeLayout var4, LinearLayout var5, Block var6, EditText var7) {
						this.this$0 = var1;
						this.val$args = var2;
						this.val$edLabel = var3;
						this.val$blockArea = var4;
						this.val$removeArea = var5;
						this.val$block = var6;
						this.val$edName = var7;
				}
				
				public void onClick(View var1) {
						this.val$args.add(new Pair("t", this.val$edLabel.getText().toString()));
						LogicBlockActivity.access$1200(this.this$0, this.val$blockArea, this.val$removeArea, this.val$block, this.val$edName.getText().toString(), this.val$args);
				}
		}
		
		
		
		
		class LogicBlockActivity$15 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final ArrayList val$args;
				// $FF: synthetic field
				final Block val$block;
				// $FF: synthetic field
				final RelativeLayout val$blockArea;
				// $FF: synthetic field
				final EditText val$edBoolean;
				// $FF: synthetic field
				final EditText val$edName;
				// $FF: synthetic field
				final EditText val$edNumber;
				// $FF: synthetic field
				final EditText val$edString;
				// $FF: synthetic field
				final LinearLayout val$removeArea;
				
				LogicBlockActivity$15(LogicBlockActivity var1, ArrayList var2, EditText var3, RelativeLayout var4, LinearLayout var5, Block var6, EditText var7, EditText var8, EditText var9) {
						this.this$0 = var1;
						this.val$args = var2;
						this.val$edString = var3;
						this.val$blockArea = var4;
						this.val$removeArea = var5;
						this.val$block = var6;
						this.val$edName = var7;
						this.val$edBoolean = var8;
						this.val$edNumber = var9;
				}
				
				public void onClick(View var1) {
						if(LogicBlockActivity.access$1500(this.this$0).isValid()) {
								this.val$args.add(new Pair("s", this.val$edString.getText().toString()));
								LogicBlockActivity.access$1200(this.this$0, this.val$blockArea, this.val$removeArea, this.val$block, this.val$edName.getText().toString(), this.val$args);
								ArrayList var3 = new ArrayList(Arrays.asList(DefineSource.getUsedWord(DesignActivity.getScId())));
								Iterator var4 = this.val$args.iterator();
								
								while(var4.hasNext()) {
										Pair var5 = (Pair)var4.next();
										if(!((String)var5.first).equals("t")) {
												var3.add(var5.second);
										}
								}
								
								LogicBlockActivity.access$1300(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1300(this.this$0).setText(this.val$edBoolean.getText().toString());
								LogicBlockActivity.access$1400(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1400(this.this$0).setText(this.val$edNumber.getText().toString());
								LogicBlockActivity.access$1500(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								this.val$edString.setText("");
						}
				}
		}
		
		
		
		
		class LogicBlockActivity$14 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final ArrayList val$args;
				// $FF: synthetic field
				final Block val$block;
				// $FF: synthetic field
				final RelativeLayout val$blockArea;
				// $FF: synthetic field
				final EditText val$edBoolean;
				// $FF: synthetic field
				final EditText val$edName;
				// $FF: synthetic field
				final EditText val$edNumber;
				// $FF: synthetic field
				final EditText val$edString;
				// $FF: synthetic field
				final LinearLayout val$removeArea;
				
				LogicBlockActivity$14(LogicBlockActivity var1, ArrayList var2, EditText var3, RelativeLayout var4, LinearLayout var5, Block var6, EditText var7, EditText var8, EditText var9) {
						this.this$0 = var1;
						this.val$args = var2;
						this.val$edNumber = var3;
						this.val$blockArea = var4;
						this.val$removeArea = var5;
						this.val$block = var6;
						this.val$edName = var7;
						this.val$edBoolean = var8;
						this.val$edString = var9;
				}
				
				public void onClick(View var1) {
						if(LogicBlockActivity.access$1400(this.this$0).isValid()) {
								this.val$args.add(new Pair("d", this.val$edNumber.getText().toString()));
								LogicBlockActivity.access$1200(this.this$0, this.val$blockArea, this.val$removeArea, this.val$block, this.val$edName.getText().toString(), this.val$args);
								ArrayList var3 = new ArrayList(Arrays.asList(DefineSource.getUsedWord(DesignActivity.getScId())));
								Iterator var4 = this.val$args.iterator();
								
								while(var4.hasNext()) {
										Pair var5 = (Pair)var4.next();
										if(!((String)var5.first).equals("t")) {
												var3.add(var5.second);
										}
								}
								
								LogicBlockActivity.access$1300(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1300(this.this$0).setText(this.val$edBoolean.getText().toString());
								LogicBlockActivity.access$1400(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1500(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1500(this.this$0).setText(this.val$edString.getText().toString());
								this.val$edNumber.setText("");
						}
				}
		}
		
		
		
		class LogicBlockActivity$12 implements TextWatcher {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final ArrayList val$args;
				// $FF: synthetic field
				final Block val$block;
				// $FF: synthetic field
				final RelativeLayout val$blockArea;
				// $FF: synthetic field
				final LinearLayout val$removeArea;
				
				LogicBlockActivity$12(LogicBlockActivity var1, RelativeLayout var2, LinearLayout var3, Block var4, ArrayList var5) {
						this.this$0 = var1;
						this.val$blockArea = var2;
						this.val$removeArea = var3;
						this.val$block = var4;
						this.val$args = var5;
				}
				
				public void afterTextChanged(Editable var1) {
						makeBlockWithSpec(this.val$blockArea, this.val$removeArea, this.val$block, var1.toString(), this.val$args);
				}
				
				public void beforeTextChanged(CharSequence var1, int var2, int var3, int var4) {
				}
				
				public void onTextChanged(CharSequence var1, int var2, int var3, int var4) {
				}
		}
		
		
		class LogicBlockActivity$13 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final ArrayList val$args;
				// $FF: synthetic field
				final Block val$block;
				// $FF: synthetic field
				final RelativeLayout val$blockArea;
				// $FF: synthetic field
				final EditText val$edBoolean;
				// $FF: synthetic field
				final EditText val$edName;
				// $FF: synthetic field
				final EditText val$edNumber;
				// $FF: synthetic field
				final EditText val$edString;
				// $FF: synthetic field
				final LinearLayout val$removeArea;
				
				LogicBlockActivity$13(LogicBlockActivity var1, ArrayList var2, EditText var3, RelativeLayout var4, LinearLayout var5, Block var6, EditText var7, EditText var8, EditText var9) {
						this.this$0 = var1;
						this.val$args = var2;
						this.val$edBoolean = var3;
						this.val$blockArea = var4;
						this.val$removeArea = var5;
						this.val$block = var6;
						this.val$edName = var7;
						this.val$edNumber = var8;
						this.val$edString = var9;
				}
				
				public void onClick(View var1) {
						if(LogicBlockActivity.access$1300(this.this$0).isValid()) {
								this.val$args.add(new Pair("b", this.val$edBoolean.getText().toString()));
								LogicBlockActivity.access$1200(this.this$0, this.val$blockArea, this.val$removeArea, this.val$block, this.val$edName.getText().toString(), this.val$args);
								ArrayList var3 = new ArrayList(Arrays.asList(DefineSource.getUsedWord(DesignActivity.getScId())));
								Iterator var4 = this.val$args.iterator();
								
								while(var4.hasNext()) {
										Pair var5 = (Pair)var4.next();
										if(!((String)var5.first).equals("t")) {
												var3.add(var5.second);
										}
								}
								
								LogicBlockActivity.access$1300(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1400(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1400(this.this$0).setText(this.val$edNumber.getText().toString());
								LogicBlockActivity.access$1500(this.this$0).setUsedWords((String[])var3.toArray(new String[var3.size()]));
								LogicBlockActivity.access$1500(this.this$0).setText(this.val$edString.getText().toString());
								this.val$edBoolean.setText("");
						}
				}
		}
		
		// $FF: synthetic method
		static boolean access$000(LogicBlockActivity var0) {
				return var0.isPaletteOpened;
		}
		
		// $FF: synthetic method
		static void access$100(LogicBlockActivity var0, boolean var1) {
				var0.openPalette(var1);
		}
		
		// $FF: synthetic method
		static String access$1000(LogicBlockActivity var0) {
				return var0.id;
		}
		
		// $FF: synthetic method
		static String access$1100(LogicBlockActivity var0) {
				return var0.eventName;
		}
		
		// $FF: synthetic method
		static void access$1200(LogicBlockActivity var0, ViewGroup var1, ViewGroup var2, Block var3, String var4, ArrayList var5) {
				var0.makeBlockWithSpec(var1, var2, var3, var4, var5);
		}
		
		// $FF: synthetic method
		static VariableNameValidator access$1300(LogicBlockActivity var0) {
				return var0.booleanValidator;
		}
		
		// $FF: synthetic method
		static VariableNameValidator access$1400(LogicBlockActivity var0) {
				return var0.numberValidator;
		}
		
		// $FF: synthetic method
		static VariableNameValidator access$1500(LogicBlockActivity var0) {
				return var0.stringValidator;
		}
		
		// $FF: synthetic method
		static void access$200(LogicBlockActivity var0) {
				var0.updateIconDeletePosition();
		}
		
		// $FF: synthetic method
		static void access$300(LogicBlockActivity var0) {
				var0.dragStart();
		}
		
		// $FF: synthetic method
		static void access$400(LogicBlockActivity var0) {
				var0.saveLogic();
		}
		
		/*   // $FF: synthetic method
static void access$500(LogicBlockActivity var0) {
var0.dismissProgress();
}

// $FF: synthetic method
static void access$600(LogicBlockActivity var0) {
var0.dismissProgress();
}
*/
		// $FF: synthetic method
		static Context access$700(LogicBlockActivity var0) {
				return var0.context;
		}
		
		// $FF: synthetic method
		static AlertDialog access$800(LogicBlockActivity var0) {
				return var0.mDlg;
		}
		
		// $FF: synthetic method
		static BlockPane access$900(LogicBlockActivity var0) {
				return var0.pane;
		}
		
		
		
		
		
		private void showAddListPopup() {
				View inflate = LayoutUtil.inflate(this, R.layout.logic_popup_add_list);
				Builder builder = new Builder(this);
				builder.setView(inflate);
				builder.setTitle(getString(R.string.logic_popup_title_add_list));
				RadioGroup radioGroup = (RadioGroup) inflate.findViewById(R.id.rg_type);
				EditText editText = (EditText) inflate.findViewById(R.id.ed_input);
				VariableNameValidator variableNameValidator = new VariableNameValidator(this.context, (TextInputLayout) inflate.findViewById(R.id.ti_input), DefineSource.RESERVED_WORD, DefineSource.getUsedWord(DesignActivity.getScId()), DesignDataManager.getAllNamesForValid(filename));
				editText.setPrivateImeOptions("defaultInputmode=english;");
				builder.setNegativeButton(R.string.btn_cancel, new LogicBlockActivity$9(this));
				builder.setPositiveButton(R.string.btn_accept, null);
				this.mDlg = builder.create();
				this.mDlg.setOnShowListener(new LogicBlockActivity$10(this, variableNameValidator, radioGroup, editText));
				this.mDlg.show();
		}
		
		
		
		
		class LogicBlockActivity$10 implements DialogInterface.OnShowListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final EditText val$edInput;
				// $FF: synthetic field
				final RadioGroup val$rgType;
				// $FF: synthetic field
				final VariableNameValidator val$varNameValidator;
				
				LogicBlockActivity$10(LogicBlockActivity var1, VariableNameValidator var2, RadioGroup var3, EditText var4) {
						this.this$0 = var1;
						this.val$varNameValidator = var2;
						this.val$rgType = var3;
						this.val$edInput = var4;
				}
				
				public void onShow(DialogInterface var1) {
						LogicBlockActivity.access$800(this.this$0).getButton(-1).setOnClickListener(new LogicBlockActivity$10$1(this));
				}
		}
		
		
		
		class LogicBlockActivity$10$1 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity$10 this$1;
				
				LogicBlockActivity$10$1(LogicBlockActivity$10 var1) {
						this.this$1 = var1;
				}
				
				public void onClick(View var1) {
						if(this.this$1.val$varNameValidator.isValid()) {
								byte var2 = 1;
								if(this.this$1.val$rgType.getCheckedRadioButtonId() == R.id.rb_int) {
										var2 = 1;
								} else if(this.this$1.val$rgType.getCheckedRadioButtonId() == R.id.rb_string) {
										var2 = 2;
								}
								
								String var3 = this.this$1.val$edInput.getText().toString();
								DesignDataManager.addList(LogicBlockActivity.filename, var2, var3);
								this.this$1.this$0.onBlockCategorySelect(1, -3384542);
								LogicBlockActivity.access$800(this.this$1.this$0).dismiss();
						}
				}
		}
		
		
		
		
		
		class LogicBlockActivity$9 implements DialogInterface.OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				
				LogicBlockActivity$9(LogicBlockActivity var1) {
						this.this$0 = var1;
				}
				
				public void onClick(DialogInterface var1, int var2) {
						LogicBlockActivity.access$800(this.this$0).dismiss();
				}
		}
		
		
		
		
		
		private void showAddVarPopup() {
				LinearLayout root = new LinearLayout(this);
				root.setOrientation(LinearLayout.VERTICAL);
				int pad = (int)(24 * getResources().getDisplayMetrics().density);
				root.setPadding(pad, (int)(12 * getResources().getDisplayMetrics().density), pad, (int)(20 * getResources().getDisplayMetrics().density));

				// Title/Header for declaration type
				TextView tvDecl = new TextView(this);
				tvDecl.setText("Declaration Type");
				tvDecl.setTextSize(14);
				tvDecl.setPadding(0, 0, 0, (int)(6 * getResources().getDisplayMetrics().density));
				root.addView(tvDecl);

				// Chip group for const / let
				com.google.android.material.chip.ChipGroup cgDecl = new com.google.android.material.chip.ChipGroup(this);
				cgDecl.setSingleSelection(true);
				cgDecl.setSelectionRequired(true);
				cgDecl.setChipSpacingHorizontal((int)(8 * getResources().getDisplayMetrics().density));

				com.google.android.material.chip.Chip chipConst = new com.google.android.material.chip.Chip(this);
				chipConst.setText("const");
				chipConst.setCheckable(true);
				chipConst.setChecked(true);
				chipConst.setId(androidx.core.view.ViewCompat.generateViewId());
				cgDecl.addView(chipConst);

				com.google.android.material.chip.Chip chipLet = new com.google.android.material.chip.Chip(this);
				chipLet.setText("let");
				chipLet.setCheckable(true);
				chipLet.setId(androidx.core.view.ViewCompat.generateViewId());
				cgDecl.addView(chipLet);
				root.addView(cgDecl);

				// Title/Header for data type
				TextView tvType = new TextView(this);
				tvType.setText("Data Type");
				tvType.setTextSize(14);
				tvType.setPadding(0, (int)(12 * getResources().getDisplayMetrics().density), 0, (int)(6 * getResources().getDisplayMetrics().density));
				root.addView(tvType);

				// Chip group for boolean, number, string (no map)
				com.google.android.material.chip.ChipGroup cgType = new com.google.android.material.chip.ChipGroup(this);
				cgType.setSingleSelection(true);
				cgType.setSelectionRequired(true);
				cgType.setChipSpacingHorizontal((int)(8 * getResources().getDisplayMetrics().density));

				com.google.android.material.chip.Chip chipBool = new com.google.android.material.chip.Chip(this);
				chipBool.setText("boolean");
				chipBool.setCheckable(true);
				chipBool.setId(androidx.core.view.ViewCompat.generateViewId());
				cgType.addView(chipBool);

				com.google.android.material.chip.Chip chipNum = new com.google.android.material.chip.Chip(this);
				chipNum.setText("number");
				chipNum.setCheckable(true);
				chipNum.setChecked(true);
				chipNum.setId(androidx.core.view.ViewCompat.generateViewId());
				cgType.addView(chipNum);

				com.google.android.material.chip.Chip chipStr = new com.google.android.material.chip.Chip(this);
				chipStr.setText("string");
				chipStr.setCheckable(true);
				chipStr.setId(androidx.core.view.ViewCompat.generateViewId());
				cgType.addView(chipStr);
				root.addView(cgType);

				// Enter variable name outlined text input
				com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
				til.setHint("Enter variable name");
				til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
				int cornerRadius = (int)(14 * getResources().getDisplayMetrics().density);
				til.setBoxCornerRadii(cornerRadius, cornerRadius, cornerRadius, cornerRadius);
				LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				tilLp.topMargin = (int)(16 * getResources().getDisplayMetrics().density);
				til.setLayoutParams(tilLp);

				com.google.android.material.textfield.TextInputEditText etName = new com.google.android.material.textfield.TextInputEditText(til.getContext());
				etName.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
				etName.setSingleLine(true);
				etName.setMinimumHeight((int)(56 * getResources().getDisplayMetrics().density));
				int hPadding = (int)(16 * getResources().getDisplayMetrics().density);
				int vPadding = (int)(12 * getResources().getDisplayMetrics().density);
				etName.setPadding(hPadding, vPadding, hPadding, vPadding);
				etName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
				etName.setPrivateImeOptions("defaultInputmode=english;");
				til.addView(etName);
				root.addView(til);

				VariableNameValidator validator = new VariableNameValidator(this.context != null ? this.context : this, til, DefineSource.RESERVED_WORD, DefineSource.getUsedWord(DesignActivity.getScId()), DesignDataManager.getAllNamesForValid(filename));

				com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
						.setTitle(getString(R.string.logic_popup_title_add_variable))
						.setView(root)
						.setNegativeButton(R.string.btn_cancel, null)
						.setPositiveButton(R.string.btn_accept, null);

				final androidx.appcompat.app.AlertDialog dialog = builder.create();
				if (dialog.getWindow() != null) {
						dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				}
				dialog.show();

				etName.requestFocus();
				etName.postDelayed(() -> {
						try {
								android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
								if (imm != null) {
										imm.showSoftInput(etName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
								}
						} catch (Exception ignored) {}
				}, 150);

				dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
						if (validator.isValid()) {
								int baseType = 1; // number
								if (cgType.getCheckedChipId() == chipBool.getId()) {
										baseType = 0;
								} else if (cgType.getCheckedChipId() == chipNum.getId()) {
										baseType = 1;
								} else if (cgType.getCheckedChipId() == chipStr.getId()) {
										baseType = 2;
								}

								boolean isConst = (cgDecl.getCheckedChipId() == chipConst.getId());
								int finalType = isConst ? (baseType + 4) : baseType;

								String varName = etName.getText().toString().trim();
								DesignDataManager.addVariable(filename, finalType, varName);

								onBlockCategorySelect(0, -1147626);
								dialog.dismiss();
						}
				});
		}
		
		
		
		
		class LogicBlockActivity$7 implements DialogInterface.OnShowListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final EditText val$edInput;
				// $FF: synthetic field
				final RadioGroup val$rgType;
				// $FF: synthetic field
				final VariableNameValidator val$varNameValidator;
				
				LogicBlockActivity$7(LogicBlockActivity var1, RadioGroup var2, EditText var3, VariableNameValidator var4) {
						this.this$0 = var1;
						this.val$rgType = var2;
						this.val$edInput = var3;
						this.val$varNameValidator = var4;
				}
				
				public void onShow(DialogInterface var1) {
						LogicBlockActivity.access$800(this.this$0).getButton(-1).setOnClickListener(new LogicBlockActivity$7$1(this));
				}
		}
		
		
		class LogicBlockActivity$7$1 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity$7 this$1;
				
				LogicBlockActivity$7$1(LogicBlockActivity$7 var1) {
						this.this$1 = var1;
				}
				
				public void onClick(View var1) {
						byte var2 = 1;
						if(this.this$1.val$rgType.getCheckedRadioButtonId() == R.id.rb_boolean) {
								var2 = 0;
						} else if(this.this$1.val$rgType.getCheckedRadioButtonId() == R.id.rb_int) {
								var2 = 1;
						} else if(this.this$1.val$rgType.getCheckedRadioButtonId() == R.id.rb_string) {
								var2 = 2;
						}
						
						String var3 = this.this$1.val$edInput.getText().toString();
						if(this.this$1.val$varNameValidator.isValid()) {
								DesignDataManager.addVariable(LogicBlockActivity.filename, var2, var3);
								this.this$1.this$0.onBlockCategorySelect(0, -1147626);
								LogicBlockActivity.access$800(this.this$1.this$0).dismiss();
						}
				}
		}
		
		
		
		
		
		private void showIconDelete(boolean z) {
				if (!this.bInitIconDeleteAnimation) {
						initIconDeleteAnimation();
				}
				if (this.bShowIconDelete != z) {
						this.bShowIconDelete = z;
						cancelIconDeleteAnimation();
						if (z) {
								this.aniShowIconDelete.start();
						} else {
								this.aniHideIconDelete.start();
								if (this.iconSave != null) { this.iconSave.setVisibility(View.VISIBLE); this.iconSave.setAlpha(1.0f); }
								if (this.iconDuplicate != null) { this.iconDuplicate.setVisibility(View.VISIBLE); this.iconDuplicate.setAlpha(1.0f); }
						}
				}
		}
		
		private void showRemoveListPopup() {
				View inflate = LayoutUtil.inflate(this, R.layout.property_popup_selector_single);
				Builder builder = new Builder(this);
				builder.setView(inflate);
				builder.setTitle(getString(R.string.logic_popup_title_remove_list));
				ViewGroup viewGroup = (ViewGroup) inflate.findViewById(R.id.rg_content);
				ArrayList arrayList = new ArrayList();
				Iterator it = DesignDataManager.getLists(filename).iterator();
				while (it.hasNext()) {
						viewGroup.addView(createSingleItem((String) ((Pair) it.next()).second));
				}
				builder.setNegativeButton(R.string.btn_cancel, null);
				builder.setPositiveButton(R.string.btn_accept, null);
				this.mDlg = builder.create();
				this.mDlg.setOnShowListener(new LogicBlockActivity$11(this, viewGroup));
				this.mDlg.show();
		}
		
		
		class LogicBlockActivity$11 implements DialogInterface.OnShowListener {
				// $FF: synthetic field
				final LogicBlockActivity this$0;
				// $FF: synthetic field
				final ViewGroup val$content;
				
				LogicBlockActivity$11(LogicBlockActivity var1, ViewGroup var2) {
						this.this$0 = var1;
						this.val$content = var2;
				}
				
				public void onShow(DialogInterface var1) {
						LogicBlockActivity.access$800(this.this$0).getButton(-1).setOnClickListener(new LogicBlockActivity$11$1(this));
				}
		}
		
		
		
		
		class LogicBlockActivity$11$1 implements OnClickListener {
				// $FF: synthetic field
				final LogicBlockActivity$11 this$1;
				
				LogicBlockActivity$11$1(LogicBlockActivity$11 var1) {
						this.this$1 = var1;
				}
				
				public void onClick(View var1) {
						int var2 = this.this$1.val$content.getChildCount();
						int var3 = 0;
						
						while(true) {
								if(var3 < var2) {
										RadioButton var4 = (RadioButton)this.this$1.val$content.getChildAt(var3);
										if(!var4.isChecked()) {
												++var3;
												continue;
										}
										
										if(LogicBlockActivity.access$900(this.this$1.this$0).isExistListBlock(var4.getText().toString()) || DesignDataManager.isExistListBlock(LogicBlockActivity.filename, var4.getText().toString(), LogicBlockActivity.access$1000(this.this$1.this$0) + "_" + LogicBlockActivity.access$1100(this.this$1.this$0))) {
												Toast.makeText(this.this$1.this$0.getApplicationContext(), this.this$1.this$0.getString(R.string.err_currently_used_list), 0).show();
												return;
										}
										
										DesignDataManager.removeList(LogicBlockActivity.filename, var4.getText().toString());
										this.this$1.this$0.onBlockCategorySelect(1, -3384542);
								}
								
								LogicBlockActivity.access$800(this.this$1.this$0).dismiss();
								return;
						}
				}
		}
		
		
		
		
		
		private void showRemoveVarPopup() {
				int pad = (int) LayoutUtil.getDip(this, 24.0f);
				android.widget.ScrollView scroll = new android.widget.ScrollView(this);
				LinearLayout root = new LinearLayout(this);
				root.setOrientation(LinearLayout.VERTICAL);
				root.setPadding(pad, (int) LayoutUtil.getDip(this, 12.0f), pad, (int) LayoutUtil.getDip(this, 20.0f));
				scroll.addView(root);

				final android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
				rg.setOrientation(android.widget.RadioGroup.VERTICAL);
				root.addView(rg);

				ArrayList<Pair<Integer, String>> vars = DesignDataManager.getVariables(filename);
				if (vars.isEmpty()) {
						TextView tvEmpty = new TextView(this);
						tvEmpty.setText("No variables created yet.");
						tvEmpty.setGravity(android.view.Gravity.CENTER);
						tvEmpty.setPadding(0, pad, 0, pad);
						root.addView(tvEmpty);
				} else {
						for (Pair<Integer, String> p : vars) {
								int t = p.first;
								boolean isConst = (t >= 4);
								int baseType = isConst ? (t - 4) : t;
								String typeStr = "number";
								if (baseType == 0) typeStr = "boolean";
								else if (baseType == 2) typeStr = "string";

								String label = p.second + " (" + (isConst ? "const " : "let ") + typeStr + ")";
								com.google.android.material.radiobutton.MaterialRadioButton rb = new com.google.android.material.radiobutton.MaterialRadioButton(this);
								rb.setText(label);
								rb.setTag(p.second);
								rb.setTextSize(16);
								rb.setPadding(pad / 3, pad / 2, pad / 3, pad / 2);
								rg.addView(rb);
						}
				}

				com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
						.setTitle(getString(R.string.logic_popup_title_remove_variable))
						.setView(scroll)
						.setNegativeButton(R.string.btn_cancel, null)
						.setPositiveButton(R.string.btn_accept, null);

				final androidx.appcompat.app.AlertDialog dialog = builder.create();
				dialog.setOnShowListener(new DialogInterface.OnShowListener() {
						@Override
						public void onShow(DialogInterface d) {
								dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
										@Override
										public void onClick(View v) {
												int checkedId = rg.getCheckedRadioButtonId();
												View selectedRb = rg.findViewById(checkedId);
												if (selectedRb instanceof com.google.android.material.radiobutton.MaterialRadioButton) {
														String varName = ((com.google.android.material.radiobutton.MaterialRadioButton) selectedRb).getTag().toString();
														
														if (pane.isExistVariableBlock(varName) || DesignDataManager.isExistVariableBlock(filename, varName, id + "_" + eventName)) {
																Toast.makeText(getApplicationContext(), getString(R.string.err_currently_used_variable), Toast.LENGTH_SHORT).show();
																return;
														}
														
														DesignDataManager.removeVariable(filename, varName);
														onBlockCategorySelect(0, -1147626);
												}
												dialog.dismiss();
										}
								});
						}
				});
				dialog.show();
		}
		
		
		
		
		
		
		
		private void startBlockCopyInterface() {
		}
		
		/*   private void startLogicTutorialActivity() {
Intent intent = new Intent(this.context, LogicTutorialActivity.class);
intent.setFlags(536870912);
intent.putExtra("sc_id", DesignActivity.getScId());
startActivity(intent);
}

private void startManageImageActivity() {
Intent intent = new Intent(getApplicationContext(), ManageImageActivity.class);
intent.setFlags(536870912);
intent.putExtra("sc_id", DesignActivity.getScId());
startActivityForResult(intent, 209);
}*/
		
		private void updateIconDeletePosition() {
				if (this.layoutDragActions == null) return;
				if (this.isPaletteOpened && 1 == getResources().getConfiguration().orientation) {
						((RelativeLayout.LayoutParams) this.layoutDragActions.getLayoutParams()).bottomMargin = (int) LayoutUtil.getDip(this, 240.0f);
						this.layoutDragActions.requestLayout();
						return;
				}
				((RelativeLayout.LayoutParams) this.layoutDragActions.getLayoutParams()).bottomMargin = 0;
				this.layoutDragActions.requestLayout();
		}
		
		public boolean checkValidForever() {
				int childCount = this.pane.getChildCount();
				int i = 0;
				while (i < childCount) {
						View childAt = this.pane.getChildAt(i);
						i = (!(childAt instanceof Block) || ((Block) childAt).mOpCode.equals("Forever")) ? i + 1 : i + 1;
				}
				return true;
		}
		
		public boolean checkValidZero() {
				return true;
		}
		
		public Block makeBlockFromBean(BlockBean blockBean) {
				String spec = blockBean.spec;
				String type = (blockBean.type != null && !blockBean.type.isEmpty()) ? blockBean.type : blockBean.blockType;
				int color = blockBean.color;

				java.util.List<BlockDef> defs = BlockDef.getDefinitions(this.context != null ? this.context : this);
				if (defs != null && blockBean.opCode != null) {
						for (BlockDef def : defs) {
								if (blockBean.opCode.equalsIgnoreCase(def.id) || blockBean.opCode.equalsIgnoreCase(def.getOpCode())) {
										if (spec == null || spec.isEmpty()) {
												spec = def.getSpec();
										}
										if (type == null || type.isEmpty()) {
												type = def.getType();
										}
										if (color == 0 && def.color != null && !def.color.isEmpty()) {
												try {
														color = android.graphics.Color.parseColor(def.color);
												} catch (Exception ignored) {}
										}
										break;
								}
						}
				}

				if (spec == null || spec.isEmpty()) {
						spec = blockBean.opCode != null ? blockBean.opCode : "block";
				}
				if (type == null || type.isEmpty()) {
						type = "c";
				}

				Block block = new Block(this, Integer.valueOf(blockBean.id).intValue(), spec, type, blockBean.opCode, new Object[]{Integer.valueOf(color)});
				if (blockBean.category != null) block.mCategory = blockBean.category;
				if (blockBean.code != null) block.mCode = blockBean.code;
				return block;
		}
		
		private void saveAndFinish() {
			try {
					saveLogic();
					DesignDataManager.setBlocks(this.pageName, this.id + LOGIC_NAME_SEPARATOR + this.eventName, this.pane.getBlocks());
					DesignDataManager.saveSavedLogic(this.context, this.projectId, this.pageName);

					if (this.projectId != null && !this.projectId.isEmpty() && this.pageName != null && !this.pageName.isEmpty()) {
							if (this.pageName.endsWith(".css")) {
									BlockCodeCompiler jsm = new BlockCodeCompiler(this, this.projectId);
									String compiledCode = jsm.getSource(0, this.pane.getBlocks());

									java.io.File targetStyleFile = new java.io.File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
											+ "/.dragweb/projects/" + this.projectId + "/assets/" + this.pageName);
									targetStyleFile.getParentFile().mkdirs();
									FileUtil.writeFile(targetStyleFile.getAbsolutePath(), compiledCode);
							} else if (this.pageName.endsWith(".js")) {
									BlockCodeCompiler jsm = new BlockCodeCompiler(this, this.projectId);
									String compiledCode = jsm.getSource(0, this.pane.getBlocks());

									java.io.File targetJsFile = new java.io.File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
											+ "/.dragweb/projects/" + this.projectId + "/assets/" + this.pageName);
									targetJsFile.getParentFile().mkdirs();
									FileUtil.writeFile(targetJsFile.getAbsolutePath(), compiledCode);
							}
					}
			} catch (Exception e) {
					e.printStackTrace();
			}
			setResult(RESULT_OK);
	}

		public void onBackPressed() {
				if (this.isPaletteOpened) {
						openPalette(!this.isPaletteOpened);
						return;
				}
				if (checkValidForever() && checkValidZero()) {
						saveAndFinish();
						super.onBackPressed();
				}
		}
		
		public void onBlockCategorySelect(int i, int i2) {
				this.paletteBlock.removeAllBlocks();
				PaletteSelector.CategoryItem selectedCat = null;
				for (PaletteSelector.CategoryItem cat : PaletteSelector.categoriesList) {
						if (cat.index == i) {
								selectedCat = cat;
								break;
						}
				}
				if (selectedCat == null) return;
				this.currentCategoryItem = selectedCat;

				if (selectedCat.type == 0) {
						addButtonToPalette(getString(R.string.logic_btn_add_variable), "variableAdd");
						addButtonToPalette(getString(R.string.logic_btn_remove_variable), "variableRemove");
						addVariables();
				} else if (selectedCat.type == 1) {
						addButtonToPalette(getString(R.string.logic_btn_add_list), "listAdd");
						addButtonToPalette(getString(R.string.logic_btn_remove_list), "listRemove");
						addLists();
				} else if (selectedCat.type == 4) {
						addButtonToPalette(getString(R.string.logic_btn_make_block), "blockAdd");
						addFunctions();
				} else if (selectedCat.type == 2) {
						for (BlockDef def : BlockDef.getDefinitions(this.context)) {
								if (def.category != null && def.category.equalsIgnoreCase(selectedCat.originalCategory)) {
										int blockColor;
										try {
												blockColor = (def.color != null && !def.color.isEmpty()) ? android.graphics.Color.parseColor(def.color) : i2;
										} catch (Exception ex) {
												blockColor = i2;
										}
										addBlockToPalette(def.getSpec(), def.getType(), def.getOpCode(), blockColor, new Object[0]);
								}
						}
				} else if (selectedCat.type == 3) {
						collectionBlocksMap.clear();
						java.io.File listFile = new java.io.File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/collection/blocks/list.json");
						if (listFile.exists()) {
								try {
										String json = FileUtil.readFile(listFile.getAbsolutePath());
										if (json != null && !json.trim().isEmpty()) {
												java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
												ArrayList<Map<String, Object>> collections = new com.google.gson.Gson().fromJson(json, type);
												if (collections != null) {
														for (Map<String, Object> col : collections) {
																String name = col.get("name") != null ? col.get("name").toString() : "Collection";
																int count = col.get("count") instanceof Number ? ((Number) col.get("count")).intValue() : 0;
																String shape = col.get("shape") != null ? col.get("shape").toString() : "s";
																String opCode = "col_" + name;
																int color = android.graphics.Color.parseColor("#FF2196F3");

																Object blocksObj = col.get("blocks");
																if (blocksObj != null) {
																		String blocksJson = new com.google.gson.Gson().toJson(blocksObj);
																		java.lang.reflect.Type blocksType = new com.google.gson.reflect.TypeToken<ArrayList<BlockBean>>(){}.getType();
																		ArrayList<BlockBean> colBeans = new com.google.gson.Gson().fromJson(blocksJson, blocksType);
																		if (colBeans != null && !colBeans.isEmpty()) {
																				if (count <= 0) count = colBeans.size();
																				String spec = name + " (" + count + ")";
																				collectionBlocksMap.put(opCode, colBeans);
																				addBlockToPalette(spec, shape, opCode, color, new Object[0]);
																		}
																}
														}
												}
										}
								} catch (Exception e) {
										e.printStackTrace();
								}
						}
				}
				EditText etPaletteSearch = (EditText) findViewById(R.id.et_palette_search);
				if (etPaletteSearch != null && etPaletteSearch.getText() != null && etPaletteSearch.getText().length() > 0) {
						filterPalette(etPaletteSearch.getText().toString());
				}
		}

		private boolean doesCategoryMatchQuery(PaletteSelector.CategoryItem cat, String q) {
				if (cat == null) return false;
				if (cat.name != null && cat.name.toLowerCase().contains(q)) return true;
				if (cat.originalCategory != null && cat.originalCategory.toLowerCase().contains(q)) return true;

				if (cat.type == 0) {
						String[] varOps = {"getvar", "setvarboolean", "setvarint", "increaseint", "decreaseint", "setvarstring"};
						for (String op : varOps) {
								if (op.contains(q)) return true;
						}
						ArrayList vars = DesignDataManager.getVariables(filename);
						if (vars != null) {
								for (Object obj : vars) {
										if (obj instanceof Pair) {
												String vName = (String) ((Pair) obj).second;
												if (vName != null && vName.toLowerCase().contains(q)) return true;
										}
								}
						}
				} else if (cat.type == 1) {
						String[] listOps = {"addlistint", "insertlistint", "getatlistint", "indexlistint", "containlistint",
								            "addliststr", "insertliststr", "getatliststr", "indexliststr", "containliststr",
								            "deletelist", "lengthlist", "clearlist"};
						for (String op : listOps) {
								if (op.contains(q)) return true;
						}
						ArrayList lists = DesignDataManager.getLists(filename);
						if (lists != null) {
								for (Object obj : lists) {
										if (obj instanceof Pair) {
												String lName = (String) ((Pair) obj).second;
												if (lName != null && lName.toLowerCase().contains(q)) return true;
										}
								}
						}
				} else if (cat.type == 2) {
						for (BlockDef def : BlockDef.getDefinitions(this.context)) {
								if (def.category != null && def.category.equalsIgnoreCase(cat.originalCategory)) {
										String spec = def.getSpec() != null ? def.getSpec().toLowerCase() : "";
										String op = def.getOpCode() != null ? def.getOpCode().toLowerCase() : "";
										if (spec.contains(q) || op.contains(q)) return true;
								}
						}
				} else if (cat.type == 3) {
						for (Map.Entry<String, ArrayList<BlockBean>> entry : collectionBlocksMap.entrySet()) {
								String colOp = entry.getKey().toLowerCase();
								if (colOp.contains(q)) return true;
								if (entry.getValue() != null) {
										for (BlockBean bean : entry.getValue()) {
												if (bean != null) {
														String spec = bean.spec != null ? bean.spec.toLowerCase() : "";
														String op = bean.opCode != null ? bean.opCode.toLowerCase() : "";
														if (spec.contains(q) || op.contains(q)) return true;
												}
										}
								}
						}
				} else if (cat.type == 4) {
						ArrayList funcs = DesignDataManager.getFunctions(filename);
						if (funcs != null) {
								for (Object obj : funcs) {
										if (obj instanceof Pair) {
												String fName = (String) ((Pair) obj).second;
												if (fName != null && fName.toLowerCase().contains(q)) return true;
										}
								}
						}
				}
				return false;
		}

		private void filterPalette(String query) {
				String q = query != null ? query.trim().toLowerCase() : "";
				boolean isSearching = !q.isEmpty();

				PaletteSelector.CategoryItem firstVisibleCat = null;
				boolean currentCatIsVisible = false;

				if (this.paletteSelector != null) {
						for (int i = 0; i < this.paletteSelector.getChildCount(); i++) {
								View tabView = this.paletteSelector.getChildAt(i);
								if (i < PaletteSelector.categoriesList.size()) {
										PaletteSelector.CategoryItem cat = PaletteSelector.categoriesList.get(i);
										boolean matches = !isSearching || doesCategoryMatchQuery(cat, q);
										if (matches) {
												tabView.setVisibility(View.VISIBLE);
												if (firstVisibleCat == null) {
														firstVisibleCat = cat;
												}
												if (this.currentCategoryItem != null && this.currentCategoryItem.index == cat.index) {
														currentCatIsVisible = true;
												}
										} else {
												tabView.setVisibility(View.GONE);
										}
								}
						}
				}

				if (isSearching && !currentCatIsVisible && firstVisibleCat != null) {
						onBlockCategorySelect(firstVisibleCat.index, firstVisibleCat.color);
						return;
				}

				if (this.paletteBlock == null) return;
				LinearLayout builder = (LinearLayout) this.paletteBlock.findViewById(R.id.block_builder);
				if (builder == null) return;

				for (int i = 0; i < builder.getChildCount(); i++) {
						View child = builder.getChildAt(i);
						if (child instanceof Block) {
								Block block = (Block) child;
								String opCode = block.mOpCode != null ? block.mOpCode.toLowerCase() : "";
								String spec = block.mSpec != null ? block.mSpec.toLowerCase() : "";

								if (!isSearching || opCode.contains(q) || spec.contains(q)) {
										child.setVisibility(View.VISIBLE);
										if (i > 0) {
												View prev = builder.getChildAt(i - 1);
												if (!(prev instanceof Block) && !(prev instanceof TextView)) {
														prev.setVisibility(View.VISIBLE);
												}
										}
								} else {
										child.setVisibility(View.GONE);
										if (i > 0) {
												View prev = builder.getChildAt(i - 1);
												if (!(prev instanceof Block) && !(prev instanceof TextView)) {
														prev.setVisibility(View.GONE);
												}
										}
								}
						}
				}
		}
		
		public void onClick(View view) {
				if (view.getTag() != null) {
						if (view.getTag().equals("variableAdd")) {
								showAddVarPopup();
						} else if (view.getTag().equals("variableRemove")) {
								showRemoveVarPopup();
						} else if (view.getTag().equals("listAdd")) {
								showAddListPopup();
						} else if (view.getTag().equals("listRemove")) {
								showRemoveListPopup();
						} else if (view.getTag().equals("blockAdd")) {
								showAddBlockPopup();
						}
				}
				/*     switch (view.getId()) {
case R.id.btn_cancel:
setResult(0);
finish();
return;
case R.id.btn_accept:
setResult(-1, new Intent());
finish();
return;
default:
return;
}*/
		}
		
		public void onConfigurationChanged(Configuration configuration) {
				super.onConfigurationChanged(configuration);
				allocatePalette(configuration.orientation);
				updateIconDeletePosition();
		}
		
		protected void onCreate(Bundle bundle) {
				androidx.activity.EdgeToEdge.enable(this);
				super.onCreate(bundle);

				this.projectId = getIntent().getStringExtra("project_id");
				this.pageName = getIntent().getStringExtra("page_name");

				this.id = getIntent().getStringExtra("id");
				if (this.id == null || this.id.trim().isEmpty()) {
						this.id = "onCreate";
				}

				this.eventName = getIntent().getStringExtra("event");
				if (this.eventName == null || this.eventName.trim().isEmpty()) {
						this.eventName = "initializeLogic";
				}

				filename = getIntent().getStringExtra("filename");
				if (filename == null || filename.trim().isEmpty()) {
						filename = (this.pageName != null && !this.pageName.isEmpty()) ? this.pageName : "index";
				}

				setContentView(R.layout.logic_editor);
				
				View rootLayout = findViewById(R.id.layout);
				if (rootLayout != null) {
						androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
								androidx.core.graphics.Insets insetsType = insets.getInsets(
										androidx.core.view.WindowInsetsCompat.Type.systemBars() |
										androidx.core.view.WindowInsetsCompat.Type.ime()
								);
								v.setPadding(insetsType.left, insetsType.top, insetsType.right, insetsType.bottom);
								return insets;
						});
				}
				
				this.context = this.getApplicationContext();

				String initProjId = (this.projectId != null && !this.projectId.isEmpty()) ? this.projectId : "default_project";
				String initPageName = (this.pageName != null && !this.pageName.isEmpty()) ? this.pageName : "index";
				DesignDataManager.initialize(this.context, initProjId, initPageName);

				if (DesignDataManager.isInitialized) {
						this.prefInstall = new SharedPreferenceUtil(this.context, "P1");
						this.toolbar = (Toolbar) findViewById(R.id.toolbar);
						setSupportActionBar(this.toolbar);
						findViewById(R.id.layout_main_logo).setVisibility(8);
						getSupportActionBar().setDisplayHomeAsUpEnabled(true);
						getSupportActionBar().setHomeButtonEnabled(true);
						this.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
								public void onClick(View v) {
										onBackPressed();
								}
						});
						
						BLOCK_DRAG_Y = (int)LayoutUtil.getDip(this, BLOCK_DRAG_Y);
						
						this.useVibrate = new SharedPreferenceUtil(this.context, "P12").getBoolean("P12I0", true);
						this.minDist = ViewConfiguration.get(this.context).getScaledTouchSlop();
						this.vibrator = (Vibrator) getSystemService("vibrator");
						String stringExtra = getIntent().getStringExtra("event_text");
						if (getSupportActionBar() != null) {
								if (stringExtra != null && !stringExtra.isEmpty()) {
										if (this.id != null && this.id.equals("onCreate")) {
												getSupportActionBar().setTitle(stringExtra);
										} else {
												getSupportActionBar().setTitle(this.id + " : " + stringExtra);
										}
								} else {
										getSupportActionBar().setTitle(this.id != null ? this.id : "Logic Editor");
								}
						}

						this.paletteBlock = (PaletteBlock) findViewById(R.id.palette_block);
						if (this.paletteBlock != null) {
								this.paletteSelector = (PaletteSelector) this.paletteBlock.findViewById(R.id.palette_selector);
						} else {
								this.paletteSelector = (PaletteSelector) findViewById(R.id.palette_selector);
						}

						if (this.paletteSelector != null) {
								this.paletteSelector.setOnBlockCategorySelectListener(this);
								this.paletteSelector.refreshCategories();
						}
						this.dummy = (ViewDummy) findViewById(R.id.dummy);
						this.iconDelete = (ImageView) findViewById(R.id.icon_delete);
						this.layoutDragActions = (LinearLayout) findViewById(R.id.layout_drag_actions);
						this.iconSave = (ImageView) findViewById(R.id.icon_save);
						this.iconDuplicate = (ImageView) findViewById(R.id.icon_duplicate);
						this.editor = (ViewLogicEditor) findViewById(R.id.editor);
						this.pane = this.editor.getBlockPane();
						
						if (!PaletteSelector.categoriesList.isEmpty()) {
								PaletteSelector.CategoryItem firstCat = PaletteSelector.categoriesList.get(0);
								onBlockCategorySelect(0, firstCat.color);
						} else {
								onBlockCategorySelect(0, -1147626);
						}
						this.layoutPalette = (LinearLayout) findViewById(R.id.layout_palette);
						this.areaPalette = (LinearLayout) findViewById(R.id.area_palette);
						this.fab = (FloatingActionButton) findViewById(R.id.fab_toggle_palette);
						this.fab.setOnClickListener(new View.OnClickListener() {
								public void onClick(View v) {
										openPalette(!isPaletteOpened);
								}
						});

						final FloatingActionButton fabSearchPalette = (FloatingActionButton) findViewById(R.id.fab_search_palette);
						final com.google.android.material.card.MaterialCardView cardSearchPalette = (com.google.android.material.card.MaterialCardView) findViewById(R.id.card_search_palette);
						final EditText etPaletteSearch = (EditText) findViewById(R.id.et_palette_search);

						if (fabSearchPalette != null && cardSearchPalette != null && etPaletteSearch != null) {
								etPaletteSearch.addTextChangedListener(new android.text.TextWatcher() {
										@Override
										public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

										@Override
										public void onTextChanged(CharSequence s, int start, int before, int count) {
												String text = s != null ? s.toString() : "";
												if (text.length() > 0) {
														fabSearchPalette.setImageResource(R.drawable.x);
												} else {
														fabSearchPalette.setImageResource(R.drawable.ic_search);
												}
												filterPalette(text);
										}

										@Override
										public void afterTextChanged(android.text.Editable s) {}
								});

								fabSearchPalette.setOnClickListener(v -> {
										String currentText = etPaletteSearch.getText() != null ? etPaletteSearch.getText().toString() : "";
										if (currentText.length() > 0) {
												etPaletteSearch.setText("");
										} else {
												if (cardSearchPalette.getVisibility() == View.VISIBLE) {
														cardSearchPalette.setVisibility(View.GONE);
														android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
														if (imm != null) imm.hideSoftInputFromWindow(etPaletteSearch.getWindowToken(), 0);
												} else {
														cardSearchPalette.setVisibility(View.VISIBLE);
														etPaletteSearch.requestFocus();
														android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
														if (imm != null) imm.showSoftInput(etPaletteSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
												}
										}
								});
						}
						return;
				}
				if (bundle != null) {
						backupCurrentData(bundle);
				}
				finish();
		}
		
		private void saveUndoState() {
				if (this.isUndoRedoAction || this.pane == null) return;
				try {
						ArrayList<BlockBean> blocks = this.pane.getBlocks();
						String json = new com.google.gson.Gson().toJson(blocks != null ? blocks : new ArrayList<>());
						if (!this.undoStack.isEmpty() && this.undoStack.get(this.undoStack.size() - 1).equals(json)) {
								return;
						}
						this.undoStack.add(json);
						if (this.undoStack.size() > 50) {
								this.undoStack.remove(0);
						}
						this.redoStack.clear();
						updateUndoRedoMenuState();
				} catch (Exception e) {
						e.printStackTrace();
				}
		}

		private void restoreState(String json) {
				if (json == null || this.pane == null) return;
				this.isUndoRedoAction = true;
				try {
						ArrayList<Block> allBlocks = new ArrayList<>();
						for (int i = 0; i < this.pane.getChildCount(); i++) {
								View child = this.pane.getChildAt(i);
								if (child instanceof Block && child != this.pane.getRoot()) {
										allBlocks.add((Block) child);
								}
						}
						for (Block b : allBlocks) {
								this.pane.removeBlock(b);
						}

						java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<BlockBean>>(){}.getType();
						ArrayList<BlockBean> blockBeans = new com.google.gson.Gson().fromJson(json, type);
						if (blockBeans != null && !blockBeans.isEmpty()) {
								unpackCollectionBlocks(blockBeans, 20, 20);
						}
				} catch (Exception e) {
						e.printStackTrace();
				} finally {
						this.isUndoRedoAction = false;
						updateUndoRedoMenuState();
				}
		}

		private void performUndo() {
				if (this.undoStack.size() <= 1) {
						Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
						return;
				}
				String currentState = this.undoStack.remove(this.undoStack.size() - 1);
				this.redoStack.add(currentState);
				String targetState = this.undoStack.get(this.undoStack.size() - 1);
				restoreState(targetState);
		}

		private void performRedo() {
				if (this.redoStack.isEmpty()) {
						Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
						return;
				}
				String targetState = this.redoStack.remove(this.redoStack.size() - 1);
				this.undoStack.add(targetState);
				restoreState(targetState);
		}

		private void updateUndoRedoMenuState() {
				if (this.menu == null) return;
				MenuItem itemUndo = this.menu.findItem(R.id.menu_logic_undo);
				MenuItem itemRedo = this.menu.findItem(R.id.menu_logic_redo);
				boolean canUndo = this.undoStack.size() > 1;
				boolean canRedo = !this.redoStack.isEmpty();

				int m3ColorOnSurface = com.google.android.material.color.MaterialColors.getColor(
						this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK);

				if (itemUndo != null) {
						itemUndo.setEnabled(canUndo);
						if (itemUndo.getIcon() != null) {
								itemUndo.getIcon().setTint(m3ColorOnSurface);
								itemUndo.getIcon().setAlpha(canUndo ? 255 : 100);
						}
				}
				if (itemRedo != null) {
						itemRedo.setEnabled(canRedo);
						if (itemRedo.getIcon() != null) {
								itemRedo.getIcon().setTint(m3ColorOnSurface);
								itemRedo.getIcon().setAlpha(canRedo ? 255 : 100);
						}
				}
		}

		public boolean onCreateOptionsMenu(Menu menu) {
				getMenuInflater().inflate(R.menu.logic_menu, menu);
				this.menu = menu;

				int m3ColorOnSurface = com.google.android.material.color.MaterialColors.getColor(
						this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK);

				if (menu != null) {
						for (int i = 0; i < menu.size(); i++) {
								MenuItem item = menu.getItem(i);
								if (item.getIcon() != null) {
										item.getIcon().setTint(m3ColorOnSurface);
								}
						}
				}

				Toolbar toolbarView = findViewById(R.id.toolbar);
				if (toolbarView != null && toolbarView.getNavigationIcon() != null) {
						toolbarView.getNavigationIcon().setTint(m3ColorOnSurface);
				}

				updateUndoRedoMenuState();
				return true;
		}
		
		protected void onDestroy() {
				super.onDestroy();
		}
		
		public boolean onOptionsItemSelected(MenuItem menuItem) {
				int id = menuItem.getItemId();
				if (id == R.id.menu_show_source) {
						showSourceCode();
						return true;
				} else if (id == R.id.menu_logic_undo) {
						performUndo();
						return true;
				} else if (id == R.id.menu_logic_redo) {
						performRedo();
						return true;
				}
				return super.onOptionsItemSelected(menuItem);
		}
		
		private void showSourceCode() {
				BlockCodeCompiler jsm = new BlockCodeCompiler(this, this.projectId);
				final String result = jsm.getSource(0, pane.getBlocks());
				
				final boolean isCss = isCssEvent();
				final String ext = isCss ? ".css" : ".js";

				java.io.File targetFile;
				String relPath = "";

				if (this.projectId != null && !this.projectId.isEmpty() && this.pageName != null && !this.pageName.isEmpty()) {
						String safePageName = this.pageName.endsWith(ext) ? this.pageName : (this.pageName + ext);
						targetFile = new java.io.File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
								+ "/.dragweb/projects/" + this.projectId + "/assets/" + safePageName);
						relPath = safePageName;
				} else {
						java.io.File cacheDir = new java.io.File(getCacheDir(), "preview_source");
						if (!cacheDir.exists()) cacheDir.mkdirs();
						targetFile = new java.io.File(cacheDir, (filename != null ? filename : "source") + ext);
						relPath = targetFile.getName();
				}

				try {
						if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
								targetFile.getParentFile().mkdirs();
						}
						FileUtil.writeFile(targetFile.getAbsolutePath(), result != null ? result : "");
				} catch (Exception e) {
						e.printStackTrace();
				}

				Intent intent = new Intent(this, TextEditorActivity.class);
				intent.putExtra("file_path", targetFile.getAbsolutePath());
				intent.putExtra("project_id", this.projectId != null ? this.projectId : "");
				intent.putExtra("relative_path", relPath);
				intent.putExtra("read_only", true);
				startActivity(intent);
		}

		private String highlightCodeLocal(String code, String language) {
				if (code == null) return "";
				String escaped = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
				if ("css".equalsIgnoreCase(language)) {
						escaped = escaped.replaceAll("(/\\*[\\s\\S]*?\\*/)", "<span style=\"color:#6A9955;\">$1</span>");
						escaped = escaped.replaceAll("([a-zA-Z0-9_-]+)\\s*:", "<span style=\"color:#9CDCFE;\">$1</span>:");
						escaped = escaped.replaceAll("(:\\s*)([^;\\}]+)(;)?", "$1<span style=\"color:#CE9178;\">$2</span>$3");
						escaped = escaped.replaceAll("([\\{\\}])", "<span style=\"color:#FFD700;font-weight:bold;\">$1</span>");
				} else {
						escaped = escaped.replaceAll("(//.*|/\\*[\\s\\S]*?\\*/)", "<span style=\"color:#6A9955;\">$1</span>");
						escaped = escaped.replaceAll("\\b(function|var|let|const|return|if|else|for|while|try|catch|async|await|new|this|import|export|from)\\b", "<span style=\"color:#569CD6;font-weight:bold;\">$1</span>");
						escaped = escaped.replaceAll("(['\"`][^'\"`]*['\"`])", "<span style=\"color:#CE9178;\">$1</span>");
						escaped = escaped.replaceAll("\\b(true|false|null|undefined|document|window|console|Math|JSON)\\b", "<span style=\"color:#4FC1FF;\">$1</span>");
				}
				return escaped;
		}

		private void loadHighlightedCode(android.webkit.WebView webView, String code, String language) {
				String bodyHtml = highlightCodeLocal(code, language);
				String html = "<!DOCTYPE html><html><head>" +
						"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
						"<style>" +
						"html, body { margin:0; padding:0; background:#1e1e1e; color:#d4d4d4; font-family:Consolas, 'Courier New', monospace; font-size:13px; width:100%; height:100%; }" +
						"pre { margin:0; padding:16px; box-sizing:border-box; white-space:pre-wrap; word-break:break-all; line-height:1.5; }" +
						"code { font-family:Consolas, 'Courier New', monospace; }" +
						"</style>" +
						"</head><body><pre><code>" + bodyHtml + "</code></pre></body></html>";
				webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
		}
		
		
		protected void onPostCreate(@Nullable Bundle var1) {
				super.onPostCreate(var1);
				String var2;
				if(this.eventName.equals("initializeLogic")) {
						var2 = this.getString(R.string.root_spec_initialize);
				} else if(this.eventName.equals("moreBlock")) {
						String var20 = DesignDataManager.getFunctionSpec(filename, this.id);
						var2 = this.getString(R.string.root_spec_define) + " " + var20;
				} else if(this.eventName.equals("onClick")) {
						var2 = this.getString(R.string.root_spec_when) + " " + this.id + " " + this.getString(R.string.root_spec_onclicked);
				} else if(this.eventName.equals("onCheckedChange")) {
						var2 = this.getString(R.string.root_spec_when) + " " + this.id + " " + this.getString(R.string.root_spec_oncheckchanged);
				} else if(this.eventName.equals("onItemSelected")) {
						var2 = this.getString(R.string.root_spec_when) + " " + this.id + " " + this.getString(R.string.root_spec_onitemselected);
				} else if(this.eventName.equals("onItemClicked")) {
						var2 = this.getString(R.string.root_spec_when) + " " + this.id + " " + this.getString(R.string.root_spec_onitemclicked);
				} else if(this.eventName.equals("onTextChanged")) {
						var2 = this.getString(R.string.root_spec_when) + " " + this.id + " " + " " + this.getString(R.string.root_spec_ontextchanged);
				} else {
						var2 = this.getString(R.string.root_spec_when) + " " + this.id + " " + this.eventName;
				}
				
				this.pane.addRoot(var2, this.eventName);
				ArrayList var3 = StringUtil.tokenize(var2);
				int var4 = 0;
				
				for(int var5 = 0; var5 < var3.size(); ++var5) {
						String var6 = (String)var3.get(var5);
						if(var6.charAt(0) == 37) {
								Block var11;
								if(var6.charAt(1) == 98) {
										Context var16 = this.getApplicationContext();
										int var17 = var4 + 1;
										String var18 = var6.substring(3);
										Object[] var19 = new Object[]{Integer.valueOf(-7711273)};
										var11 = new Block(var16, var17, var18, "b", "getArg", var19);
								} else if(var6.charAt(1) == 100) {
										Context var12 = this.getApplicationContext();
										int var13 = var4 + 1;
										String var14 = var6.substring(3);
										Object[] var15 = new Object[]{Integer.valueOf(-7711273)};
										var11 = new Block(var12, var13, var14, "d", "getArg", var15);
								} else {
										if(var6.charAt(1) != 115) {
												continue;
										}
										
										Context var7 = this.getApplicationContext();
										int var8 = var4 + 1;
										String var9 = var6.substring(3);
										Object[] var10 = new Object[]{Integer.valueOf(-7711273)};
										var11 = new Block(var7, var8, var9, "s", "getArg", var10);
								}
								
								var11.setBlockType(1);
								this.pane.addView(var11);
								this.pane.getRoot().replaceArgWithBlock((BlockBase)this.pane.getRoot().args.get(var4), var11);
								var11.setOnTouchListener(this);
								++var4;
						}
				}
				
				this.pane.getRoot().fixLayout();
				this.loadLogic();
				this.allocatePalette(this.getResources().getConfiguration().orientation);
				saveUndoState();
		}
		
		protected void onResume() {
				super.onResume();
				BlockDef.clearCache();
				CategoryDef.clearCache();
				if (this.paletteSelector != null) {
						this.paletteSelector.refreshCategories();
				}
		}
		
		protected void onSaveInstanceState(Bundle bundle) {
				super.onSaveInstanceState(bundle);
		}
		
		public boolean onTouch(View view, MotionEvent motionEvent) {
				int action = motionEvent.getAction();
				if (action == 0) {
						this.isDragged = false;
						this.handler.postDelayed(this.longPressed, (long) (ViewConfiguration.getLongPressTimeout() / 2));
						this.posInitX = motionEvent.getX();
						this.posInitY = motionEvent.getY();
						this.currentTouchedView = view;
						return true;
				} else if (action == 2) {
						if (this.isDragged) {
								this.handler.removeCallbacks(this.longPressed);
								this.dummy.moveDummy(view, motionEvent.getX(), motionEvent.getY(), this.posInitX, this.posInitY, (float)BLOCK_DRAG_X, (float)BLOCK_DRAG_Y);
								boolean isCollection = view instanceof Block && ((Block) view).mOpCode != null && collectionBlocksMap.containsKey(((Block) view).mOpCode);
								if (hitTestIcon(this.iconDelete, motionEvent.getRawX(), motionEvent.getRawY())) {
										this.dummy.setAllow(true);
										activeIconDelete(true);
										activeIconSave(false);
										activeIconDuplicate(false);
										return true;
								} else if (!isCollection && hitTestIcon(this.iconSave, motionEvent.getRawX(), motionEvent.getRawY())) {
										this.dummy.setAllow(true);
										activeIconDelete(false);
										activeIconSave(true);
										activeIconDuplicate(false);
										return true;
								} else if (!isCollection && hitTestIcon(this.iconDuplicate, motionEvent.getRawX(), motionEvent.getRawY())) {
										this.dummy.setAllow(true);
										activeIconDelete(false);
										activeIconSave(false);
										activeIconDuplicate(true);
										return true;
								}
								activeIconDelete(false);
								activeIconSave(false);
								activeIconDuplicate(false);
								this.dummy.getDummyPosition(this.posDummy);
								if (this.editor.hitTest((float) this.posDummy[0], (float) this.posDummy[1])) {
										this.dummy.setAllow(true);
										this.pane.updateFeedbackFor((Block) view, this.posDummy[0], this.posDummy[1]);
								} else {
										this.dummy.setAllow(false);
										this.pane.hideFeedbackShape();
								}
								return true;
						} else if (Math.abs(this.posInitX - motionEvent.getX()) < ((float) this.minDist) && Math.abs(this.posInitY - motionEvent.getY()) < ((float) this.minDist)) {
								return false;
						} else {
								this.currentTouchedView = null;
								this.handler.removeCallbacks(this.longPressed);
								return false;
						}
				} else if (action == 1) {
						this.currentTouchedView = null;
						this.handler.removeCallbacks(this.longPressed);
						if (this.isDragged) {
								this.paletteBlock.setDragEnabled(true);
								this.editor.setScrollEnabled(true);
								this.dummy.setDummyVisibility(8);
								if (this.dummy.getAllow()) {
										if (this.bActiveIconDelete) {
												activeIconDelete(false);
												if (view instanceof Block && ((Block) view).mOpCode != null && collectionBlocksMap.containsKey(((Block) view).mOpCode)) {
														confirmDeleteCollection(((Block) view).mOpCode);
												} else {
														this.pane.removeBlock((Block) view);
												}
										} else if (this.bActiveIconSave) {
												activeIconSave(false);
												this.pane.setVisibleBlock((Block) view, 0);
												if (view instanceof Block) {
														restoreOriginalRelation((Block) view);
														saveBlockToCollection((Block) view);
												}
										} else if (this.bActiveIconDuplicate) {
												activeIconDuplicate(false);
												this.pane.setVisibleBlock((Block) view, 0);
												if (view instanceof Block) {
														restoreOriginalRelation((Block) view);
														duplicateBlock((Block) view);
												}
										} else if (view instanceof Block) {
												this.dummy.getDummyPosition(this.posDummy);
												if (((Block) view).getBlockType() == 1) {
														Block dropped = this.pane.blockDropped((Block) view, this.posDummy[0], this.posDummy[1], false);
														if (dropped != null) {
																if (collectionBlocksMap.containsKey(dropped.mOpCode)) {
																		ArrayList<BlockBean> colBeans = collectionBlocksMap.get(dropped.mOpCode);
																		this.pane.removeBlock(dropped);
																		if (colBeans != null && !colBeans.isEmpty()) {
																				unpackCollectionBlocks(colBeans, this.posDummy[0], this.posDummy[1]);
																		}
																} else {
																		dropped.setOnTouchListener(this);
																}
														}
												} else {
														this.pane.setVisibleBlock((Block) view, 0);
														this.pane.blockDropped((Block) view, this.posDummy[0], this.posDummy[1], true);
												}
												this.pane.draggingDone();
										}
								} else if (((Block) view).getBlockType() == 0) {
										this.pane.setVisibleBlock((Block) view, 0);
										restoreOriginalRelation((Block) view);
								}
								this.dummy.setAllow(false);
								showIconDelete(false);
								this.isDragged = false;
								saveUndoState();
								return true;
						}
						if ((view instanceof Block) && ((Block) view).getBlockType() == 0) {
								((Block) view).actionClick(motionEvent.getX(), motionEvent.getY());
						}
						return false;
				} else if (action == 3) {
						this.handler.removeCallbacks(this.longPressed);
						this.isDragged = false;
						return false;
				} else if (action != 8) {
						return true;
				} else {
						this.handler.removeCallbacks(this.longPressed);
						this.isDragged = false;
						return false;
				}
		}

		private void restoreOriginalRelation(Block view) {
				if (view == null) return;
				if (this.originalParent != null) {
						if (this.originalInsertOption == 0) {
								this.originalParent.nextBlock = ((Integer) view.getTag()).intValue();
						} else if (this.originalInsertOption == 2) {
								this.originalParent.subStack1 = ((Integer) view.getTag()).intValue();
						} else if (this.originalInsertOption == 3) {
								this.originalParent.subStack2 = ((Integer) view.getTag()).intValue();
						} else if (this.originalInsertOption == 5 && this.originalArgIndex >= 0 && this.originalArgIndex < this.originalParent.args.size()) {
								this.originalParent.replaceArgWithBlock((BlockBase) this.originalParent.args.get(this.originalArgIndex), view);
						}
						view.parentBlock = this.originalParent;
						this.originalParent.topBlock().fixLayout();
				} else {
						view.topBlock().fixLayout();
				}
		}

		private void duplicateBlock(Block block) {
				if (block == null) return;
				ArrayList<Block> list = block.getAllChildren();
				DesignDataManager.copyBlocks(filename, list);
				pasteCopiedBlocks();
				Toast.makeText(this, "Block duplicated", Toast.LENGTH_SHORT).show();
		}

		private void saveBlockToCollection(final Block block) {
				if (block == null) return;
				final java.io.File collectionFile = new java.io.File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/collection/blocks/list.json");

				UniversalM3Dialog dialog = new UniversalM3Dialog(this);
				dialog.setTitle("Save to Collection")
				      .setHint("Collection Name")
				      .setInitialValue("My Collection")
				      .showTextInputWithValidation(
				          name -> {
				              if (name == null || name.trim().isEmpty()) {
				                  return "Collection name cannot be empty";
				              }
				              String safeName = name.trim();
				              if (collectionFile.exists()) {
				                  try {
				                      String json = FileUtil.readFile(collectionFile.getAbsolutePath());
				                      if (json != null && !json.trim().isEmpty()) {
				                          java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
				                          ArrayList<Map<String, Object>> list = new com.google.gson.Gson().fromJson(json, type);
				                          if (list != null) {
				                              for (Map<String, Object> item : list) {
				                                  Object existingName = item.get("name");
				                                  if (existingName != null && safeName.equalsIgnoreCase(existingName.toString().trim())) {
				                                      return "Collection with this name already exists";
				                                  }
				                              }
				                          }
				                      }
				                  } catch (Exception ignored) {}
				              }
				              return null;
				          },
				          name -> {
				              String safeName = name.trim();
				              try {
				                  ArrayList<Map<String, Object>> collectionList = new ArrayList<>();
				                  if (collectionFile.exists()) {
				                      String json = FileUtil.readFile(collectionFile.getAbsolutePath());
				                      if (json != null && !json.trim().isEmpty()) {
				                          java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
				                          ArrayList<Map<String, Object>> existingList = new com.google.gson.Gson().fromJson(json, type);
				                          if (existingList != null) {
				                              collectionList.addAll(existingList);
				                          }
				                      }
				                  }

				                  ArrayList<BlockBean> blockBeans = new ArrayList<>();
				                  ArrayList<Block> children = block.getAllChildren();
				                  if (children != null && !children.isEmpty()) {
				                      for (Block child : children) {
				                          if (child != null && child.getBean() != null) {
				                              blockBeans.add(child.getBean());
				                          }
				                      }
				                  } else if (block != null && block.getBean() != null) {
				                      blockBeans.add(block.getBean());
				                  }

				                  Map<String, Object> newCollection = new java.util.LinkedHashMap<>();
				                  newCollection.put("name", safeName);
				                  newCollection.put("count", blockBeans.size());
				                  newCollection.put("shape", block.mType != null ? block.mType : "s");
				                  newCollection.put("blocks", blockBeans);

				                  collectionList.add(newCollection);

				                  java.io.File parent = collectionFile.getParentFile();
				                  if (parent != null && !parent.exists()) {
				                      parent.mkdirs();
				                  }

				                  String outputJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(collectionList);
				                  FileUtil.writeFile(collectionFile.getAbsolutePath(), outputJson);

				                  Toast.makeText(this, "Saved collection: " + safeName, Toast.LENGTH_SHORT).show();
				                  if (paletteSelector != null) {
				                      paletteSelector.refresh();
				                  }
				                  if (this.currentCategoryItem != null && this.currentCategoryItem.type == 3) {
				                      onBlockCategorySelect(this.currentCategoryItem.index, this.currentCategoryItem.color);
				                  }
				              } catch (Exception e) {
				                  e.printStackTrace();
				                  Toast.makeText(this, "Failed to save collection: " + e.getMessage(), Toast.LENGTH_LONG).show();
				              }
				          }
				      );
		}

		private void confirmDeleteCollection(final String opCode) {
				if (opCode == null || !opCode.startsWith("col_")) return;
				final String colName = opCode.substring(4);

				new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
						.setTitle("Delete Collection")
						.setMessage("Are you sure you want to delete collection '" + colName + "'?")
						.setPositiveButton("Delete", (dialog, which) -> {
								try {
										java.io.File listFile = new java.io.File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/collection/blocks/list.json");
										if (listFile.exists()) {
												String json = FileUtil.readFile(listFile.getAbsolutePath());
												if (json != null && !json.trim().isEmpty()) {
														java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
														ArrayList<Map<String, Object>> collections = new com.google.gson.Gson().fromJson(json, type);
														if (collections != null) {
																Iterator<Map<String, Object>> it = collections.iterator();
																while (it.hasNext()) {
																		Map<String, Object> item = it.next();
																		Object nameObj = item.get("name");
																		if (nameObj != null && colName.equalsIgnoreCase(nameObj.toString().trim())) {
																				it.remove();
																		}
																}
																String newJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(collections);
																FileUtil.writeFile(listFile.getAbsolutePath(), newJson);
														}
												}
										}
										collectionBlocksMap.remove(opCode);
										Toast.makeText(this, "Collection deleted: " + colName, Toast.LENGTH_SHORT).show();
										if (paletteSelector != null) {
												paletteSelector.refresh();
										}
										if (this.currentCategoryItem != null && this.currentCategoryItem.type == 3) {
												onBlockCategorySelect(this.currentCategoryItem.index, this.currentCategoryItem.color);
										}
								} catch (Exception e) {
										e.printStackTrace();
										Toast.makeText(this, "Failed to delete collection: " + e.getMessage(), Toast.LENGTH_SHORT).show();
								}
						})
						.setNegativeButton("Cancel", null)
						.show();
		}

		private void unpackCollectionBlocks(ArrayList<BlockBean> rawBlocks, int posX, int posY) {
				if (rawBlocks == null || rawBlocks.isEmpty()) return;

				Map<Integer, Integer> idMapping = new HashMap<>();
				int baseId = this.pane.blockId + 10;

				for (BlockBean bean : rawBlocks) {
						int oldId = safeParseInt(bean.id);
						int newId = baseId++;
						idMapping.put(Integer.valueOf(oldId), Integer.valueOf(newId));
				}
				this.pane.blockId = baseId + 10;

				ArrayList<BlockBean> rebasedList = new ArrayList<>();
				for (BlockBean oldBean : rawBlocks) {
						BlockBean newBean = new BlockBean();
						newBean.copy(oldBean);
						int oldId = safeParseInt(oldBean.id);
						Integer mappedId = idMapping.get(Integer.valueOf(oldId));
						newBean.id = String.valueOf(mappedId != null ? mappedId.intValue() : baseId++);

						if (oldBean.subStack1 >= 0 && idMapping.containsKey(Integer.valueOf(oldBean.subStack1))) {
								newBean.subStack1 = idMapping.get(Integer.valueOf(oldBean.subStack1)).intValue();
						} else {
								newBean.subStack1 = -1;
						}

						if (oldBean.subStack2 >= 0 && idMapping.containsKey(Integer.valueOf(oldBean.subStack2))) {
								newBean.subStack2 = idMapping.get(Integer.valueOf(oldBean.subStack2)).intValue();
						} else {
								newBean.subStack2 = -1;
						}

						if (oldBean.nextBlock >= 0 && idMapping.containsKey(Integer.valueOf(oldBean.nextBlock))) {
								newBean.nextBlock = idMapping.get(Integer.valueOf(oldBean.nextBlock)).intValue();
						} else {
								newBean.nextBlock = -1;
						}

						newBean.parameters = new ArrayList<>();
						if (oldBean.parameters != null) {
								for (String param : oldBean.parameters) {
										if (param != null && param.startsWith("@")) {
												int oldParamId = safeParseInt(param.substring(1));
												if (idMapping.containsKey(Integer.valueOf(oldParamId))) {
														newBean.parameters.add("@" + idMapping.get(Integer.valueOf(oldParamId)));
												} else {
														newBean.parameters.add("");
												}
										} else {
												newBean.parameters.add(param != null ? param : "");
										}
								}
						}

						rebasedList.add(newBean);
				}

				Map<Integer, Block> blockMap = new HashMap<>();
				for (BlockBean bean : rebasedList) {
						Block block = makeBlockFromBean(bean);
						int bId = ((Integer) block.getTag()).intValue();
						blockMap.put(Integer.valueOf(bId), block);
						this.pane.addBlock(block, posX, posY);
						block.setOnTouchListener(this);
				}

				for (BlockBean bean : rebasedList) {
						int bId = safeParseInt(bean.id);
						Block block = blockMap.get(Integer.valueOf(bId));
						if (block != null) {
								if (bean.subStack1 >= 0 && blockMap.containsKey(Integer.valueOf(bean.subStack1))) {
										block.insertBlockSub1(blockMap.get(Integer.valueOf(bean.subStack1)));
								}
								if (bean.subStack2 >= 0 && blockMap.containsKey(Integer.valueOf(bean.subStack2))) {
										block.insertBlockSub2(blockMap.get(Integer.valueOf(bean.subStack2)));
								}
								if (bean.nextBlock >= 0 && blockMap.containsKey(Integer.valueOf(bean.nextBlock))) {
										block.insertBlock(blockMap.get(Integer.valueOf(bean.nextBlock)));
								}
								for (int p = 0; p < bean.parameters.size(); p++) {
										String paramVal = bean.parameters.get(p);
										if (paramVal != null && !paramVal.isEmpty()) {
												if (paramVal.startsWith("@")) {
														int refId = safeParseInt(paramVal.substring(1));
														if (blockMap.containsKey(Integer.valueOf(refId))) {
																block.replaceArgWithBlock((BlockBase) block.args.get(p), blockMap.get(Integer.valueOf(refId)));
														}
												} else if (p < block.args.size() && block.args.get(p) instanceof BlockArg) {
														((BlockArg) block.args.get(p)).setArgValue(paramVal);
														block.recalcWidthToParent();
												}
										}
								}
						}
				}

				Block firstRootBlock = null;
				for (BlockBean bean : rebasedList) {
						int bId = safeParseInt(bean.id);
						Block block = blockMap.get(Integer.valueOf(bId));
						if (block != null && block.parentBlock == null) {
								this.pane.getRoot().insertBlock(block);
								if (firstRootBlock == null) {
										firstRootBlock = block;
								}
						}
				}

				if (firstRootBlock != null) {
						firstRootBlock.topBlock().fixLayout();
				}
				this.pane.calculateWidthHeight();
		}
}
