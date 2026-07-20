package sketchweb.gl;

import android.content.Context;
import android.util.Pair;

import com.google.gson.Gson;
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


    public static void addFunction(String str, String str2, String str3) {
        Pair pair = new Pair(str2, str3);
        if (!mapFunctions.containsKey(str)) {
            mapFunctions.put(str, new ArrayList());
        }
        ((ArrayList) mapFunctions.get(str)).add(pair);
    }

    public static void addList(String str, int i, String str2) {
        Pair pair = new Pair(Integer.valueOf(i), str2);
        if (!mapLists.containsKey(str)) {
            mapLists.put(str, new ArrayList());
        }
        ((ArrayList) mapLists.get(str)).add(pair);
    }

    public static void addVariable(String str, int i, String str2) {
        Pair pair = new Pair(Integer.valueOf(i), str2);
        if (!mapVariables.containsKey(str)) {
            mapVariables.put(str, new ArrayList());
        }
        ((ArrayList) mapVariables.get(str)).add(pair);
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
        return !mapBlocks.containsKey(str) ? new HashMap() : (Map) mapBlocks.get(str);
    }

    public static ArrayList<String> getAllLists(String str) {
        ArrayList<String> arrayList = new ArrayList();
        if (mapLists.containsKey(str)) {
            ArrayList arrayList2 = (ArrayList) mapLists.get(str);
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
     /*   it = getAllViews(ProjectFileManager.getXmlNameFromJava(str)).iterator();
        while (it.hasNext()) {
            arrayList.add(SourceUtil.getVariableNameFromId(((ViewBean) it.next()).id));
        }
        it = getComponents(str).iterator();
        while (it.hasNext()) {
            arrayList.add(((ComponentBean) it.next()).componentId);
        }*/
        return arrayList;
    }



    public static ArrayList<BlockBean> getBlocks(String str, String str2) {
        if (!mapBlocks.containsKey(str)) {
            return new ArrayList();
        }
        Map map = (Map) mapBlocks.get(str);
        return map == null ? new ArrayList() : !map.containsKey(str2) ? new ArrayList() : (ArrayList) map.get(str2);
    }

    public static ArrayList<BlockBean> getClipboard(String str) {
        return !mapCopiedBlocks.containsKey(str) ? new ArrayList() : (ArrayList) mapCopiedBlocks.get(str);
    }

    public static String getFunctionSpec(String str, String str2) {
        if (!mapFunctions.containsKey(str)) {
            return "";
        }
        ArrayList arrayList = (ArrayList) mapFunctions.get(str);
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
        return mapFunctions.containsKey(str) ? (ArrayList) mapFunctions.get(str) : new ArrayList();
    }

    public static ArrayList<Pair<Integer, String>> getLists(String str) {
        return mapLists.containsKey(str) ? (ArrayList) mapLists.get(str) : new ArrayList();
    }

    public static ArrayList<String> getListsByType(String str, int i) {
        ArrayList<String> arrayList = new ArrayList();
        if (mapLists.containsKey(str)) {
            ArrayList arrayList2 = (ArrayList) mapLists.get(str);
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
        return mapVariables.containsKey(str) ? (ArrayList) mapVariables.get(str) : new ArrayList();
    }

    public static ArrayList<String> getVariablesByType(String str, int i) {
        ArrayList<String> arrayList = new ArrayList();
        Iterator it = ((ArrayList) mapVariables.get(str)).iterator();
        while (it.hasNext()) {
            Pair<Integer, String> pair = (Pair<Integer, String>) it.next();
            if (((Integer) pair.first).intValue() == i) {
                arrayList.add(pair.second);
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

    public static void initialize(Context context, String str) {
        initMaps();
    //    prefView = new SharedPreferenceUtil(context, "P19");
        prefLogic = new SharedPreferenceUtil(context, "P20");
        isInitialized = true;
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

    public static void loadSavedLogic() {
        HashMap<String, Object> readState = prefLogic.readState();
        if (readState != null && readState.size() > 0) {
            for (Entry entry : readState.entrySet()) {
                String str = (String) entry.getKey();
                int indexOf = str.indexOf("_");
                if (indexOf >= 0) {
                    String substring = str.substring(VAR_TYPE_BOOLEAN, indexOf);
                    String substring2 = str.substring(indexOf + VAR_TYPE_INT);
                    String str2 = (String) entry.getValue();
                    if (str2 != null && str2.length() > 0) {
                        int i = -1;
                        switch (substring2.hashCode()) {
                            case -1291329255:
                                if (substring2.equals("events")) {
                                    i = 4;
                                    break;
                                }
                                break;
                            case -447446250:
                                if (substring2.equals("components")) {
                                    i = 3;
                                    break;
                                }
                                break;
                            case 116519:
                                if (substring2.equals("var")) {
                                    i = VAR_TYPE_BOOLEAN;
                                    break;
                                }
                                break;
                            case 3154628:
                                if (substring2.equals("func")) {
                                    i = VAR_TYPE_STRING;
                                    break;
                                }
                                break;
                            case 3322014:
                                if (substring2.equals("list")) {
                                    i = VAR_TYPE_INT;
                                    break;
                                }
                                break;
                        }
                        switch (i) {
                            case VAR_TYPE_BOOLEAN /*0*/:
                                mapVariables.put(substring, parseVariableString(str2));
                                break;
                            case VAR_TYPE_INT /*1*/:
                                mapLists.put(substring, parseListString(str2));
                                break;
                            case VAR_TYPE_STRING /*2*/:
                                mapFunctions.put(substring, parseFunctionString(str2));
                                break;
                            case 3:
                           //     mapComponents.put(substring, parseJsonToComponentArray(str2));
                                break;
                            case 4:
                      //          mapEvents.put(substring, parseJsonToEventArray(str2));
                                break;
                            default:
                                if (!mapBlocks.containsKey(substring)) {
                                    mapBlocks.put(substring, new HashMap());
                                }
                                ((HashMap) mapBlocks.get(substring)).put(substring2, parseJsonToBlockArray(str2));
                                break;
                        }
                    }
                }
            }
        }
    }

    public static void saveSavedLogic(String filename) {
        if (prefLogic == null) return;
        HashMap<String, Object> state = new HashMap<>();

        ArrayList<Pair<Integer, String>> vars = mapVariables.get(filename);
        if (vars != null) {
            StringBuilder sb = new StringBuilder();
            for (Pair<Integer, String> p : vars) {
                sb.append(p.first).append(":").append(p.second).append("\n");
            }
            state.put(filename + "_var", sb.toString());
        } else {
            state.put(filename + "_var", "");
        }

        ArrayList<Pair<Integer, String>> lists = mapLists.get(filename);
        if (lists != null) {
            StringBuilder sb = new StringBuilder();
            for (Pair<Integer, String> p : lists) {
                sb.append(p.first).append(":").append(p.second).append("\n");
            }
            state.put(filename + "_list", sb.toString());
        } else {
            state.put(filename + "_list", "");
        }

        ArrayList<Pair<String, String>> funcs = mapFunctions.get(filename);
        if (funcs != null) {
            StringBuilder sb = new StringBuilder();
            for (Pair<String, String> p : funcs) {
                sb.append(p.first).append(":").append(p.second).append("\n");
            }
            state.put(filename + "_func", sb.toString());
        } else {
            state.put(filename + "_func", "");
        }

        HashMap<String, ArrayList<BlockBean>> blocksMap = mapBlocks.get(filename);
        if (blocksMap != null) {
            Gson gson = new Gson();
            for (Entry<String, ArrayList<BlockBean>> entry : blocksMap.entrySet()) {
                String eventKey = entry.getKey();
                ArrayList<BlockBean> list = entry.getValue();
                StringBuilder sb = new StringBuilder();
                if (list != null) {
                    for (BlockBean b : list) {
                        sb.append(gson.toJson(b)).append("\n");
                    }
                }
                state.put(filename + "_" + eventKey, sb.toString());
            }
        }

        prefLogic.writeState(state);
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
        if (!mapBlocks.containsKey(str)) {
            mapBlocks.put(str, new HashMap());
        }
        ((Map) mapBlocks.get(str)).put(str2, arrayList);
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
