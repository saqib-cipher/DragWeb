package sketchweb.gl;

import android.content.Context;
import android.util.Pair;

import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class DesignDataManager {
    public static final int VAR_TYPE_BOOLEAN = 0;
    public static final int VAR_TYPE_INT = 1;
    public static final int VAR_TYPE_STRING = 2;
    public static boolean isInitialized = false;
    protected static Map<String, HashMap<String, ArrayList<BlockBean>>> mapBlocks = new HashMap<>();
  //  protected static Map<String, ArrayList<ComponentBean>> mapComponents;
    protected static Map<String, ArrayList<BlockBean>> mapCopiedBlocks = new HashMap<>();
 //   protected static Map<String, ArrayList<EventBean>> mapEvents;
    protected static Map<String, ArrayList<Pair<String, String>>> mapFunctions = new HashMap<>();
    protected static Map<String, ArrayList<Pair<Integer, String>>> mapLists = new HashMap<>();
    protected static Map<String, ArrayList<Pair<Integer, String>>> mapVariables = new HashMap<>();
 //   protected static Map<String, ArrayList<ViewBean>> mapViews;
    public static SharedPreferenceUtil prefLogic;
   // public static SharedPreferenceUtil prefView;


    public static String getCleanPageName(String name) {
        if (name == null || name.isEmpty()) return "index";
        return name.replace(".html", "")
                   .replace(".css", "")
                   .replace(".js", "")
                   .replace("/", "_")
                   .replace("\\", "_");
    }

    public static void addFunction(String str, String str2, String str3) {
        String clean = getCleanPageName(str);
        Pair pair = new Pair(str2, str3);
        if (!mapFunctions.containsKey(clean)) {
            mapFunctions.put(clean, new ArrayList());
        }
        ((ArrayList) mapFunctions.get(clean)).add(pair);
    }

    public static void addList(String str, int i, String str2) {
        String clean = getCleanPageName(str);
        Pair pair = new Pair(Integer.valueOf(i), str2);
        if (!mapLists.containsKey(clean)) {
            mapLists.put(clean, new ArrayList());
        }
        ((ArrayList) mapLists.get(clean)).add(pair);
    }

    public static void addVariable(String str, int i, String str2) {
        String clean = getCleanPageName(str);
        Pair pair = new Pair(Integer.valueOf(i), str2);
        if (!mapVariables.containsKey(clean)) {
            mapVariables.put(clean, new ArrayList());
        }
        ((ArrayList) mapVariables.get(clean)).add(pair);
    }
/*
    public static void addView(String str, ViewBean viewBean) {
        if (!mapViews.containsKey(str)) {
            mapViews.put(str, new ArrayList());
        }
        ((ArrayList) mapViews.get(str)).add(viewBean);
    }
*/
    public static void clearClipboard(String str) {
        if (mapCopiedBlocks.containsKey(str) && ((ArrayList) mapCopiedBlocks.get(str)) != null) {
            ((ArrayList) mapCopiedBlocks.get(str)).clear();
        }
    }

    public static void copyBlocks(String str, ArrayList<Block> arrayList) {
        clearClipboard(str);
        ArrayList arrayList2 = new ArrayList();
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Block block = (Block) it.next();
            BlockBean blockBean = new BlockBean();
            BlockBean bean = block.getBean();
            blockBean.copy(bean);
            
            int parsedIdVal = 0;
            try {
                parsedIdVal = Integer.parseInt(bean.id.trim());
            } catch (Exception e) {
                parsedIdVal = Math.abs(bean.id.hashCode()) % 1000000;
            }
            blockBean.id = String.valueOf(99000000 + parsedIdVal);

            if (bean.subStack1 > 0) {
                blockBean.subStack1 = bean.subStack1 + 99000000;
            }
            if (bean.subStack2 > 0) {
                blockBean.subStack2 = bean.subStack2 + 99000000;
            }
            if (bean.nextBlock > 0) {
                blockBean.nextBlock = bean.nextBlock + 99000000;
            }
            blockBean.parameters.clear();
            Iterator it2 = bean.parameters.iterator();
            while (it2.hasNext()) {
                String str2 = (String) it2.next();
                if (str2.length() <= VAR_TYPE_INT || str2.charAt(VAR_TYPE_BOOLEAN) != '@') {
                    blockBean.parameters.add(str2);
                } else {
                    int parsedParamId = 0;
                    try {
                        parsedParamId = Integer.parseInt(str2.substring(VAR_TYPE_INT).trim());
                    } catch (Exception e) {
                        parsedParamId = Math.abs(str2.substring(VAR_TYPE_INT).hashCode()) % 1000000;
                    }
                    blockBean.parameters.add('@' + String.valueOf(99000000 + parsedParamId));
                }
            }
            arrayList2.add(blockBean);
        }
        mapCopiedBlocks.put(str, arrayList2);
    }

    public static Map<String, ArrayList<BlockBean>> getAllBlocks(String str) {
        String clean = getCleanPageName(str);
        return !mapBlocks.containsKey(clean) ? new HashMap() : (Map) mapBlocks.get(clean);
    }

    public static ArrayList<String> getAllLists(String str) {
        String clean = getCleanPageName(str);
        ArrayList<String> arrayList = new ArrayList();
        if (mapLists.containsKey(clean)) {
            ArrayList arrayList2 = (ArrayList) mapLists.get(clean);
            if (arrayList2 != null) {
                Iterator it = arrayList2.iterator();
                while (it.hasNext()) {
                    arrayList.add(((Pair<Integer, String>) it.next()).second);
                }
            }
        }
        return arrayList;
    }

    public static ArrayList<String> getAllNamesForValid(String str) {
        ArrayList<String> arrayList = new ArrayList();
        Iterator it = getVariables(str).iterator();
        while (it.hasNext()) {
            arrayList.add(((Pair<Integer, String>) it.next()).second);
        }
        it = getLists(str).iterator();
        while (it.hasNext()) {
            arrayList.add(((Pair<Integer, String>) it.next()).second);
        }
        it = getFunctions(str).iterator();
        while (it.hasNext()) {
            arrayList.add(((Pair<String, String>) it.next()).first);
        }
        return arrayList;
    }

    public static ArrayList<BlockBean> getBlocks(String str, String str2) {
        String clean = getCleanPageName(str);
        if (!mapBlocks.containsKey(clean)) {
            return new ArrayList();
        }
        Map map = (Map) mapBlocks.get(clean);
        return map == null ? new ArrayList() : !map.containsKey(str2) ? new ArrayList() : (ArrayList) map.get(str2);
    }

    public static ArrayList<BlockBean> getClipboard(String str) {
        String clean = getCleanPageName(str);
        return !mapCopiedBlocks.containsKey(clean) ? new ArrayList() : (ArrayList) mapCopiedBlocks.get(clean);
    }

    public static String getFunctionSpec(String str, String str2) {
        String clean = getCleanPageName(str);
        if (!mapFunctions.containsKey(clean)) {
            return "";
        }
        ArrayList arrayList = (ArrayList) mapFunctions.get(clean);
        if (arrayList == null) {
            return "";
        }
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Pair pair = (Pair) it.next();
            if (((String) pair.first).equals(str2)) {
                return (String) pair.second;
            }
        }
        return "";
    }

    public static ArrayList<Pair<String, String>> getFunctions(String str) {
        String clean = getCleanPageName(str);
        return mapFunctions.containsKey(clean) ? (ArrayList) mapFunctions.get(clean) : new ArrayList();
    }

    public static ArrayList<Pair<Integer, String>> getLists(String str) {
        String clean = getCleanPageName(str);
        return mapLists.containsKey(clean) ? (ArrayList) mapLists.get(clean) : new ArrayList();
    }

    public static ArrayList<String> getListsByType(String str, int i) {
        String clean = getCleanPageName(str);
        ArrayList<String> arrayList = new ArrayList();
        if (mapLists.containsKey(clean)) {
            ArrayList arrayList2 = (ArrayList) mapLists.get(clean);
            if (arrayList2 != null) {
                Iterator it = arrayList2.iterator();
                while (it.hasNext()) {
                    Pair<Integer, String> pair = (Pair<Integer, String>) it.next();
                    if (((Integer) pair.first).intValue() == i) {
                        arrayList.add(pair.second);
                    }
                }
            }
        }
        return arrayList;
    }

    public static ArrayList<Pair<Integer, String>> getVariables(String str) {
        String clean = getCleanPageName(str);
        return mapVariables.containsKey(clean) ? (ArrayList) mapVariables.get(clean) : new ArrayList();
    }

    public static ArrayList<String> getVariablesByType(String str, int i) {
        String clean = getCleanPageName(str);
        ArrayList<String> arrayList = new ArrayList();
        if (mapVariables.containsKey(clean) && mapVariables.get(clean) != null) {
            Iterator it = ((ArrayList) mapVariables.get(clean)).iterator();
            while (it.hasNext()) {
                Pair<Integer, String> pair = (Pair<Integer, String>) it.next();
                if (((Integer) pair.first).intValue() == i) {
                    arrayList.add(pair.second);
                }
            }
        }
        return arrayList;
    }



    protected static void initMaps() {
   /*     if (mapViews != null) {
            mapViews.clear();
        }*/
        if (mapBlocks != null) {
            mapBlocks.clear();
        }
        if (mapVariables != null) {
            mapVariables.clear();
        }
        if (mapLists != null) {
            mapLists.clear();
        }
    /*    if (mapComponents != null) {
            mapComponents.clear();
        }
        if (mapEvents != null) {
            mapEvents.clear();
        }*/
      //  mapViews = new HashMap();
        mapBlocks = new HashMap();
        mapVariables = new HashMap();
        mapLists = new HashMap();
        mapFunctions = new HashMap();
  //      mapComponents = new HashMap();
    //    mapEvents = new HashMap();
        mapCopiedBlocks = new HashMap();
    }

    public static String currentProjectId = "";
    public static String currentPageName = "index";

    public static void initialize(Context context, String projectId, String pageName) {
        String newProjectId = (projectId != null && !projectId.isEmpty()) ? projectId : "default_project";
        String newPageName = (pageName != null && !pageName.isEmpty()) ? pageName : "index";

        if (!newProjectId.equals(currentProjectId)) {
            initMaps();
            currentProjectId = newProjectId;
        }
        currentPageName = newPageName;
        prefLogic = new SharedPreferenceUtil(context, "P20_" + currentProjectId);
        isInitialized = true;
        loadSavedLogic(context, currentProjectId, currentPageName);
    }

    public static void initialize(Context context, String projectId) {
        initialize(context, projectId, "index");
    }

    public static boolean isExistClipboard(String str) {
        if (!mapCopiedBlocks.containsKey(str) || mapCopiedBlocks.get(str) == null) {
            return false;
        }
        return ((ArrayList) mapCopiedBlocks.get(str)).size() > 0;
    }

    public static boolean isExistDefinedBlock(String str, String str2) {
        HashMap<String, ArrayList<BlockBean>> map = mapBlocks.get(str);
        if (map == null) {
            return false;
        }
        for (Entry entry : map.entrySet()) {
            if (!((String) entry.getKey()).equals(str2 + "_" + "moreBlock")) {
                Iterator it = ((ArrayList) entry.getValue()).iterator();
                while (it.hasNext()) {
                    BlockBean blockBean = (BlockBean) it.next();
                    if (blockBean.opCode.equals("definedFunc")) {
                        int indexOf = blockBean.spec.indexOf(" ");
                        if ((indexOf > 0 ? blockBean.spec.substring(VAR_TYPE_BOOLEAN, indexOf) : blockBean.spec).equals(str2)) {
                            return true;
                        }
                    }
                }
                continue;
            }
        }
        return false;
    }

    public static boolean isExistListBlock(String str, String str2, String str3) {
        HashMap<String, ArrayList<BlockBean>> map = mapBlocks.get(str);
        if (map == null) {
            return false;
        }
        for (Entry entry : map.entrySet()) {
            if (!((String) entry.getKey()).equals(str3)) {
                Iterator it = ((ArrayList) entry.getValue()).iterator();
                while (it.hasNext()) {
                    BlockBean blockBean = (BlockBean) it.next();
                    String str4 = blockBean.opCode;
                    int i = -1;
                    switch (str4.hashCode()) {
                        case -1384861688:
                            if (str4.equals("getAtListInt")) {
                                i = 6;
                                break;
                            }
                            break;
                        case -1384851894:
                            if (str4.equals("getAtListStr")) {
                                i = 7;
                                break;
                            }
                            break;
                        case -1271141237:
                            if (str4.equals("clearList")) {
                                i = 3;
                                break;
                            }
                            break;
                        case -329562760:
                            if (str4.equals("insertListInt")) {
                                i = 11;
                                break;
                            }
                            break;
                        case -329552966:
                            if (str4.equals("insertListStr")) {
                                i = 12;
                                break;
                            }
                            break;
                        case -96313603:
                            if (str4.equals("containListInt")) {
                                i = VAR_TYPE_INT;
                                break;
                            }
                            break;
                        case -96303809:
                            if (str4.equals("containListStr")) {
                                i = VAR_TYPE_STRING;
                                break;
                            }
                            break;
                        case 762282303:
                            if (str4.equals("indexListInt")) {
                                i = 8;
                                break;
                            }
                            break;
                        case 762292097:
                            if (str4.equals("indexListStr")) {
                                i = 9;
                                break;
                            }
                            break;
                        case 1160674468:
                            if (str4.equals("lengthList")) {
                                i = VAR_TYPE_BOOLEAN;
                                break;
                            }
                            break;
                        case 1764351209:
                            if (str4.equals("deleteList")) {
                                i = 10;
                                break;
                            }
                            break;
                        case 2090179216:
                            if (str4.equals("addListInt")) {
                                i = 4;
                                break;
                            }
                            break;
                        case 2090189010:
                            if (str4.equals("addListStr")) {
                                i = 5;
                                break;
                            }
                            break;
                    }
                    switch (i) {
                        case VAR_TYPE_BOOLEAN /*0*/:
                        case VAR_TYPE_INT /*1*/:
                        case VAR_TYPE_STRING /*2*/:
                        case 3:
                            if (!((String) blockBean.parameters.get(VAR_TYPE_BOOLEAN)).equals(str2)) {
                                break;
                            }
                            return true;
                        case 4:
                        case 5:
                        case 6:
                        case 7:
                        case 8:
                        case 9:
                        case 10:
                            if (!((String) blockBean.parameters.get(VAR_TYPE_INT)).equals(str2)) {
                                break;
                            }
                            return true;
                        case 11:
                        case 12:
                            if (!((String) blockBean.parameters.get(VAR_TYPE_STRING)).equals(str2)) {
                                break;
                            }
                            return true;
                        default:
                            break;
                    }
                }
                continue;
            }
        }
        return false;
    }

    public static boolean isExistVariableBlock(String str, String str2, String str3) {
        HashMap<String, ArrayList<BlockBean>> map = mapBlocks.get(str);
        if (map == null) {
            return false;
        }
        for (Entry entry : map.entrySet()) {
            if (!((String) entry.getKey()).equals(str3)) {
                Iterator it = ((ArrayList) entry.getValue()).iterator();
                while (it.hasNext()) {
                    BlockBean blockBean = (BlockBean) it.next();
                    String str4 = blockBean.opCode;
                    int i = -1;
                    switch (str4.hashCode()) {
                        case -1920517885:
                            if (str4.equals("setVarBoolean")) {
                                i = VAR_TYPE_INT;
                                break;
                            }
                            break;
                        case -1377080719:
                            if (str4.equals("decreaseInt")) {
                                i = 5;
                                break;
                            }
                            break;
                        case -1249347599:
                            if (str4.equals("getVar")) {
                                i = VAR_TYPE_BOOLEAN;
                                break;
                            }
                            break;
                        case 657721930:
                            if (str4.equals("setVarInt")) {
                                i = VAR_TYPE_STRING;
                                break;
                            }
                            break;
                        case 754442829:
                            if (str4.equals("increaseInt")) {
                                i = 4;
                                break;
                            }
                            break;
                        case 845089750:
                            if (str4.equals("setVarString")) {
                                i = 3;
                                break;
                            }
                            break;
                    }
                    switch (i) {
                        case VAR_TYPE_BOOLEAN /*0*/:
                            if (!blockBean.spec.equals(str2)) {
                                break;
                            }
                            return true;
                        case VAR_TYPE_INT /*1*/:
                        case VAR_TYPE_STRING /*2*/:
                        case 3:
                        case 4:
                        case 5:
                            if (!((String) blockBean.parameters.get(VAR_TYPE_BOOLEAN)).equals(str2)) {
                                break;
                            }
                            return true;
                        default:
                            break;
                    }
                }
                continue;
            }
        }
        return false;
    }

    public static class PageLogicData {
        public Map<String, ArrayList<BlockBean>> blocks = new HashMap<>();
        public ArrayList<Pair<Integer, String>> variables = new ArrayList<>();
        public ArrayList<Pair<Integer, String>> lists = new ArrayList<>();
        public ArrayList<Pair<String, String>> functions = new ArrayList<>();
    }

    public static void loadSavedLogic(Context context, String projectId, String pageName) {
        if (projectId == null || projectId.isEmpty()) projectId = currentProjectId;
        if (projectId == null || projectId.isEmpty()) projectId = "default_project";
        if (pageName == null || pageName.isEmpty()) pageName = currentPageName;
        if (pageName == null || pageName.isEmpty()) pageName = "index";
        String cleanPage = getCleanPageName(pageName);

        // Single source of truth: .dragweb/projects/<projectId>/<page>_logic.json
        File extLogicFile = new File(android.os.Environment.getExternalStorageDirectory(),
            ".dragweb/projects/" + projectId + "/" + cleanPage + "_logic.json");

        String json = null;
        if (extLogicFile.exists()) {
            json = FileUtil.readFile(extLogicFile.getAbsolutePath());
        }

        if (!mapBlocks.containsKey(cleanPage)) {
            mapBlocks.put(cleanPage, new HashMap<>());
        }
        if (!mapVariables.containsKey(cleanPage)) {
            mapVariables.put(cleanPage, new ArrayList<>());
        }
        if (!mapLists.containsKey(cleanPage)) {
            mapLists.put(cleanPage, new ArrayList<>());
        }
        if (!mapFunctions.containsKey(cleanPage)) {
            mapFunctions.put(cleanPage, new ArrayList<>());
        }

        if (json == null || json.trim().isEmpty() || json.trim().equals("{}") || json.trim().equals("[]")) {
            return; // No saved logic for this page
        }

        try {
            Gson gson = new Gson();

            // Try structured PageLogicData format first
            PageLogicData data = gson.fromJson(json, PageLogicData.class);
            if (data != null && data.blocks != null) {
                mapBlocks.put(cleanPage, new HashMap<>(data.blocks));
                if (data.variables != null) mapVariables.put(cleanPage, new ArrayList<>(data.variables));
                if (data.lists != null) mapLists.put(cleanPage, new ArrayList<>(data.lists));
                if (data.functions != null) mapFunctions.put(cleanPage, new ArrayList<>(data.functions));
                return;
            }

            // Fallback: direct List<BlockBean> array format
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BlockBean>>(){}.getType();
            ArrayList<BlockBean> blockList = gson.fromJson(json, listType);
            if (blockList != null && !blockList.isEmpty() && blockList.get(0) != null && blockList.get(0).opCode != null) {
                HashMap<String, ArrayList<BlockBean>> pageBlocks = new HashMap<>();
                pageBlocks.put("onCreate_initializeLogic", blockList);
                mapBlocks.put(cleanPage, pageBlocks);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadSavedLogic() {
        loadSavedLogic(null, currentProjectId, currentPageName);
    }

    public static void saveSavedLogic(Context context, String projectId, String pageName) {
        if (projectId == null || projectId.isEmpty()) projectId = currentProjectId;
        if (projectId == null || projectId.isEmpty()) projectId = "default_project";
        if (pageName == null || pageName.isEmpty()) pageName = currentPageName;
        if (pageName == null || pageName.isEmpty()) pageName = "index";
        String cleanPage = getCleanPageName(pageName);

        HashMap<String, ArrayList<BlockBean>> blocksMap = mapBlocks.get(cleanPage);
        if (blocksMap == null && !mapBlocks.containsKey(cleanPage)) {
            return; // Don't overwrite disk with empty data if cleanPage was never loaded
        }

        PageLogicData data = new PageLogicData();

        if (blocksMap != null) {
            data.blocks.putAll(blocksMap);
        }

        ArrayList<Pair<Integer, String>> vars = mapVariables.get(cleanPage);
        if (vars != null) {
            data.variables.addAll(vars);
        }

        ArrayList<Pair<Integer, String>> lists = mapLists.get(cleanPage);
        if (lists != null) {
            data.lists.addAll(lists);
        }

        ArrayList<Pair<String, String>> funcs = mapFunctions.get(cleanPage);
        if (funcs != null) {
            data.functions.addAll(funcs);
        }

        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data);

        // Single source of truth: .dragweb/projects/<projectId>/<page>_logic.json
        File extDir = new File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId);
        if (!extDir.exists()) extDir.mkdirs();
        File extFile = new File(extDir, cleanPage + "_logic.json");
        FileUtil.writeFile(extFile.getAbsolutePath(), json);
    }

    public static void saveAllSavedLogic(Context context, String projectId) {
        if (projectId == null || projectId.isEmpty()) projectId = currentProjectId;
        if (projectId == null || projectId.isEmpty()) return;
        for (String page : new ArrayList<>(mapBlocks.keySet())) {
            saveSavedLogic(context, projectId, page);
        }
    }

    public static void saveSavedLogic(String filename) {
        saveSavedLogic(null, currentProjectId, filename);
    }


 

    public static ArrayList<Pair<String, String>> parseFunctionString(String str) {
        ArrayList<Pair<String, String>> arrayList = new ArrayList();
        while (true) {
            int indexOf = str.indexOf("\n");
            if (indexOf > 0) {
                String trim = str.substring(VAR_TYPE_BOOLEAN, indexOf).trim();
                if (trim.length() > 0 && trim.indexOf(":") >= 0) {
                    arrayList.add(new Pair(trim.substring(VAR_TYPE_BOOLEAN, trim.indexOf(":")), trim.substring(trim.indexOf(":") + VAR_TYPE_INT)));
                    if (indexOf >= str.length() - 1) {
                        break;
                    }
                    str = str.substring(indexOf + VAR_TYPE_INT);
                } else {
                    str = str.substring(indexOf + VAR_TYPE_INT);
                }
            } else {
                break;
            }
        }
        return arrayList;
    }

    protected static ArrayList<BlockBean> parseJsonToBlockArray(String str) {
        Gson gson = new Gson();
        ArrayList<BlockBean> arrayList = new ArrayList();
        while (str != null && str.length() > 0) {
            int indexOf = str.indexOf("\n");
            if (indexOf <= 0 || str.charAt(VAR_TYPE_BOOLEAN) != '{') {
                break;
            }
            arrayList.add(gson.fromJson(str.substring(VAR_TYPE_BOOLEAN, indexOf).trim(), BlockBean.class));
            if (indexOf >= str.length() - 1) {
                break;
            }
            str = str.substring(indexOf + VAR_TYPE_INT);
        }
        return arrayList;
    }


    public static ArrayList<Pair<Integer, String>> parseListString(String str) {
        ArrayList<Pair<Integer, String>> arrayList = new ArrayList();
        while (true) {
            int indexOf = str.indexOf("\n");
            if (indexOf > 0) {
                String trim = str.substring(VAR_TYPE_BOOLEAN, indexOf).trim();
                if (trim.length() > 0 && trim.indexOf(":") >= 0) {
                    String substring = trim.substring(VAR_TYPE_BOOLEAN, trim.indexOf(":"));
                    arrayList.add(new Pair(Integer.valueOf(substring), trim.substring(trim.indexOf(":") + VAR_TYPE_INT)));
                    if (indexOf >= str.length() - 1) {
                        break;
                    }
                    str = str.substring(indexOf + VAR_TYPE_INT);
                } else {
                    str = str.substring(indexOf + VAR_TYPE_INT);
                }
            } else {
                break;
            }
        }
        return arrayList;
    }

    public static ArrayList<Pair<Integer, String>> parseVariableString(String str) {
        ArrayList<Pair<Integer, String>> arrayList = new ArrayList();
        while (true) {
            int indexOf = str.indexOf("\n");
            if (indexOf > 0) {
                String trim = str.substring(VAR_TYPE_BOOLEAN, indexOf).trim();
                if (trim.length() > 0 && trim.indexOf(":") >= 0) {
                    String substring = trim.substring(VAR_TYPE_BOOLEAN, trim.indexOf(":"));
                    arrayList.add(new Pair(Integer.valueOf(substring), trim.substring(trim.indexOf(":") + VAR_TYPE_INT)));
                    if (indexOf >= str.length() - 1) {
                        break;
                    }
                    str = str.substring(indexOf + VAR_TYPE_INT);
                } else {
                    str = str.substring(indexOf + VAR_TYPE_INT);
                }
            } else {
                break;
            }
        }
        return arrayList;
    }


    public static void removeBlocks(String str, String str2) {
        if (mapBlocks.containsKey(str)) {
            Map map = (Map) mapBlocks.get(str);
            if (map != null && map.containsKey(str2)) {
                map.remove(str2);
            }
        }
    }

    public static void removeFunction(String str, String str2) {
        if (mapFunctions.containsKey(str)) {
            ArrayList arrayList = (ArrayList) mapFunctions.get(str);
            if (arrayList != null) {
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    Pair pair = (Pair) it.next();
                    if (((String) pair.first).equals(str2)) {
                        arrayList.remove(pair);
                        break;
                    }
                }
                if (((HashMap) mapBlocks.get(str)).containsKey(str2 + "_" + "moreBlock")) {
                    ((HashMap) mapBlocks.get(str)).remove(str2 + "_" + "moreBlock");
                }
            }
        }
    }

    public static void removeList(String str, String str2) {
        if (mapLists.containsKey(str)) {
            ArrayList arrayList = (ArrayList) mapLists.get(str);
            if (arrayList != null) {
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    Pair pair = (Pair) it.next();
                    if (((String) pair.second).equals(str2)) {
                        arrayList.remove(pair);
                        return;
                    }
                }
            }
        }
    }

    public static void removeVariable(String str, String str2) {
        if (mapVariables.containsKey(str)) {
            ArrayList arrayList = (ArrayList) mapVariables.get(str);
            if (arrayList != null) {
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    Pair pair = (Pair) it.next();
                    if (((String) pair.second).equals(str2)) {
                        arrayList.remove(pair);
                        return;
                    }
                }
            }
        }
    }


    public static void setBlocks(String str, String str2, ArrayList<BlockBean> arrayList) {
        String clean = getCleanPageName(str);
        if (!mapBlocks.containsKey(clean)) {
            mapBlocks.put(clean, new HashMap());
        }
        ((Map) mapBlocks.get(clean)).put(str2, arrayList);
    }

    public static class WidgetSelectorData {
        public ArrayList<String> ids = new ArrayList<>();
        public ArrayList<String> classes = new ArrayList<>();
        public ArrayList<String> tags = new ArrayList<>();
    }

    public static WidgetSelectorData getWidgetSelectorData(Context context, String projectId, String pageName) {
        WidgetSelectorData data = new WidgetSelectorData();
        data.tags.addAll(java.util.List.of(
            "div", "span", "p", "a", "h1", "h2", "h3", "h4", "h5", "h6",
            "button", "img", "input", "textarea", "select", "ul", "ol", "li",
            "table", "tr", "td", "th", "header", "footer", "nav", "section", "article", "aside"
        ));

        if (projectId == null || projectId.isEmpty() || pageName == null || pageName.isEmpty()) {
            return data;
        }

        String cleanPage = pageName.replace(".html", "").replace(".css", "").replace(".js", "");
        String path = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/pages/" + cleanPage + ".json";
        java.io.File file = new java.io.File(path);
        if (!file.exists() && "index".equals(cleanPage)) {
            String fallbackPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/layout.json";
            file = new java.io.File(fallbackPath);
        }
        if (!file.exists()) {
            file = new java.io.File(context.getFilesDir(), "projects/" + projectId + "_" + cleanPage + ".json");
        }

        if (file.exists()) {
            try {
                String json = FileUtil.readFile(file.getAbsolutePath());
                if (json != null && !json.isEmpty()) {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
                    ArrayList<Map<String, Object>> widgetTree = new com.google.gson.Gson().fromJson(json, type);
                    if (widgetTree != null) {
                        extractIdsAndClasses(widgetTree, data);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    private static void extractIdsAndClasses(java.util.List<Map<String, Object>> nodes, WidgetSelectorData data) {
        if (nodes == null) return;
        for (Map<String, Object> node : nodes) {
            if (node == null) continue;
            if (node.containsKey("id")) {
                String id = String.valueOf(node.get("id")).trim();
                if (!id.isEmpty() && !data.ids.contains(id)) {
                    data.ids.add(id);
                }
            }
            if (node.containsKey("classes")) {
                String classesStr = String.valueOf(node.get("classes")).trim();
                if (!classesStr.isEmpty()) {
                    String[] parts = classesStr.split("\\s+");
                    for (String part : parts) {
                        String cleanPart = part.trim();
                        if (!cleanPart.isEmpty() && !data.classes.contains(cleanPart)) {
                            data.classes.add(cleanPart);
                        }
                    }
                }
            }
            if (node.containsKey("children")) {
                try {
                    java.util.List<Map<String, Object>> children = (java.util.List<Map<String, Object>>) node.get("children");
                    extractIdsAndClasses(children, data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
