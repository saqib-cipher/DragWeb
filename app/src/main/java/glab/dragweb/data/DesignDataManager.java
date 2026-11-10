package glab.dragweb.data;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;
import glab.dragweb.SketchApplication;

import android.content.Context;
import android.util.Pair;

import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class DesignDataManager {
    public static final int VAR_TYPE_BOOLEAN = 0;
    public static final int VAR_TYPE_INT = 1;
    public static final int VAR_TYPE_STRING = 2;
    public static boolean isInitialized = false;
    public static Map<String, HashMap<String, ArrayList<BlockBean>>> mapBlocks = new HashMap<>();
    public static Map<String, ArrayList<BlockBean>> mapCopiedBlocks = new HashMap<>();
    public static Map<String, ArrayList<Pair<String, String>>> mapFunctions = new HashMap<>();
    public static Map<String, ArrayList<Pair<Integer, String>>> mapLists = new HashMap<>();
    public static Map<String, ArrayList<Pair<Integer, String>>> mapVariables = new HashMap<>();
    public static SharedPreferenceUtil prefLogic;

    public static String getCleanPageName(String name) {
        if (name == null || name.isEmpty()) return "index";
        return name.replace(".html", "")
                   .replace(".css", "")
                   .replace(".js", "")
                   .replace("/", "_")
                   .replace("\\", "_");
    }

    public static class MoreBlockData {
        public String name;
        public String type;
        public String spec;
        public String linkedFile;
        public ArrayList<BlockBean> blocks;
    }

    public static File getMoreBlocksFile(String projectId) {
        File dir = new File(FileUtil.getDragWebDir(), "projects/" + projectId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "moreblocks.json");
    }

    public static ArrayList<MoreBlockData> getProjectMoreBlocks(String projectId) {
        ArrayList<MoreBlockData> list = new ArrayList<>();
        if (projectId == null || projectId.isEmpty()) return list;
        File f = getMoreBlocksFile(projectId);
        if (FileUtil.exists(f)) {
            try {
                String json = FileUtil.readFile(f.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<MoreBlockData>>(){}.getType();
                    ArrayList<MoreBlockData> parsed = new com.google.gson.Gson().fromJson(json, type);
                    if (parsed != null) list = parsed;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public static void saveProjectMoreBlock(String projectId, MoreBlockData data) {
        if (projectId == null || projectId.isEmpty() || data == null) return;
        ArrayList<MoreBlockData> list = getProjectMoreBlocks(projectId);
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            MoreBlockData existing = list.get(i);
            if (existing != null && existing.name != null && existing.name.equals(data.name) 
                && existing.linkedFile != null && existing.linkedFile.equals(data.linkedFile)) {
                list.set(i, data);
                updated = true;
                break;
            }
        }
        if (!updated) {
            list.add(data);
        }
        File f = getMoreBlocksFile(projectId);
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(list);
        FileUtil.writeFile(f.getAbsolutePath(), json);

        addFunction(data.linkedFile, data.type != null ? data.type : " ", data.spec != null ? data.spec : data.name);

        saveSavedLogic(null, projectId, data.linkedFile);
    }

    public static boolean isMoreBlockUsedInProject(String funcName, String spec) {
        if (funcName == null || funcName.trim().isEmpty()) return false;
        String targetName = funcName.trim();
        String targetSpec = spec != null ? spec.trim() : targetName;

        if (mapBlocks != null) {
            for (HashMap<String, ArrayList<BlockBean>> pageBlocks : mapBlocks.values()) {
                if (pageBlocks != null) {
                    for (Map.Entry<String, ArrayList<BlockBean>> entry : pageBlocks.entrySet()) {
                        String k = entry.getKey();
                        if (k.equals("func_" + targetName) || k.equals(targetName + "_moreBlock") || k.endsWith("_" + targetName)) {
                            continue;
                        }
                        ArrayList<BlockBean> list = entry.getValue();
                        if (list != null) {
                            for (BlockBean bean : list) {
                                if (bean != null && "definedFunc".equals(bean.opCode)) {
                                    String bSpec = bean.spec != null ? bean.spec.trim() : "";
                                    if (targetName.equals(bSpec) || targetSpec.equals(bSpec) || bSpec.startsWith(targetName + " ")) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void deleteProjectMoreBlock(String projectId, String funcName, String linkedFile) {
        if (projectId == null || projectId.isEmpty()) return;
        ArrayList<MoreBlockData> list = getProjectMoreBlocks(projectId);
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            MoreBlockData existing = list.get(i);
            if (existing != null && existing.name != null && existing.name.equals(funcName)) {
                list.remove(i);
                removed = true;
            }
        }
        if (removed) {
            File f = getMoreBlocksFile(projectId);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(list);
            FileUtil.writeFile(f.getAbsolutePath(), json);
        }

        String funcKey = funcName != null ? funcName : "";
        String cleanPage = getCleanPageName(linkedFile);
        File logicFile = new File(FileUtil.getDragWebDir(),
            "projects/" + projectId + "/" + cleanPage + "_func_" + funcKey + "_logic.json");
        FileUtil.deleteFile(logicFile.getAbsolutePath());

        if (mapFunctions.containsKey(cleanPage)) {
            ArrayList funcs = mapFunctions.get(cleanPage);
            if (funcs != null) {
                for (int i = funcs.size() - 1; i >= 0; i--) {
                    Object obj = funcs.get(i);
                    if (obj instanceof Pair) {
                        Pair p = (Pair) obj;
                        String fName = p.first != null ? (String) p.first : (String) p.second;
                        if (funcName != null && funcName.equals(fName)) {
                            funcs.remove(i);
                        }
                    }
                }
            }
        }

        if (mapBlocks != null) {
            for (HashMap<String, ArrayList<BlockBean>> pageBlocks : mapBlocks.values()) {
                if (pageBlocks != null) {
                    pageBlocks.remove("func_" + funcKey);
                    pageBlocks.remove(funcKey + "_moreBlock");
                }
            }
        }

        saveSavedLogic(null, projectId, linkedFile);
    }

    public static class ProjectListData {
        public int type;
        public String name;
        public String linkedFile;
    }

    public static File getListsFile(String projectId) {
        File dir = new File(FileUtil.getDragWebDir(), "projects/" + projectId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "lists.json");
    }

    public static ArrayList<ProjectListData> getProjectLists(String projectId) {
        ArrayList<ProjectListData> list = new ArrayList<>();
        if (projectId == null || projectId.isEmpty()) return list;
        File f = getListsFile(projectId);
        if (FileUtil.exists(f)) {
            try {
                String json = FileUtil.readFile(f.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<ProjectListData>>(){}.getType();
                    ArrayList<ProjectListData> parsed = new com.google.gson.Gson().fromJson(json, type);
                    if (parsed != null) list = parsed;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public static void saveProjectList(String projectId, int listType, String listName, String linkedFile) {
        if (projectId == null || projectId.isEmpty() || listName == null || listName.isEmpty()) return;
        ArrayList<ProjectListData> list = getProjectLists(projectId);
        boolean exists = false;
        for (ProjectListData existing : list) {
            if (existing != null && listName.equals(existing.name)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            ProjectListData item = new ProjectListData();
            item.type = listType;
            item.name = listName;
            item.linkedFile = linkedFile;
            list.add(item);
            File f = getListsFile(projectId);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(list);
            FileUtil.writeFile(f.getAbsolutePath(), json);
        }
        addList(linkedFile, listType, listName);
    }

    public static void deleteProjectList(String projectId, String listName, String linkedFile) {
        if (projectId == null || projectId.isEmpty() || listName == null) return;
        ArrayList<ProjectListData> list = getProjectLists(projectId);
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            ProjectListData existing = list.get(i);
            if (existing != null && listName.equals(existing.name)) {
                list.remove(i);
                removed = true;
            }
        }
        if (removed) {
            File f = getListsFile(projectId);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(list);
            FileUtil.writeFile(f.getAbsolutePath(), json);
        }

        String clean = getCleanPageName(linkedFile);
        if (mapLists.containsKey(clean)) {
            ArrayList arr = mapLists.get(clean);
            if (arr != null) {
                for (int i = arr.size() - 1; i >= 0; i--) {
                    Object obj = arr.get(i);
                    if (obj instanceof Pair) {
                        Pair p = (Pair) obj;
                        if (listName.equals(p.second)) {
                            arr.remove(i);
                        }
                    }
                }
            }
        }
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
        Map<String, ArrayList<BlockBean>> map = mapBlocks.get(clean);
        if (map == null) return new ArrayList();
        if (map.containsKey(str2)) {
            ArrayList<BlockBean> list = map.get(str2);
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        // Fallback for page-load aliases
        if ("onPageLoad_onPageLoad".equals(str2) || "onCreate_initializeLogic".equals(str2) || "initializeLogic_initializeLogic".equals(str2) || "onPageLoad".equals(str2) || "initializeLogic".equals(str2)) {
            if (map.containsKey("onPageLoad_onPageLoad")) {
                ArrayList<BlockBean> list = map.get("onPageLoad_onPageLoad");
                if (list != null && !list.isEmpty()) return list;
            }
            if (map.containsKey("onCreate_initializeLogic")) {
                ArrayList<BlockBean> list = map.get("onCreate_initializeLogic");
                if (list != null && !list.isEmpty()) return list;
            }
            if (map.containsKey(clean)) {
                ArrayList<BlockBean> list = map.get(clean);
                if (list != null && !list.isEmpty()) return list;
            }
            if (map.containsKey(str)) {
                ArrayList<BlockBean> list = map.get(str);
                if (list != null && !list.isEmpty()) return list;
            }
        }
        if (map.containsKey(str2)) {
            ArrayList<BlockBean> list = map.get(str2);
            return list != null ? list : new ArrayList();
        }
        return new ArrayList();
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

    public static ArrayList<Pair<Integer, String>> getLists(String str, String projectId) {
        ArrayList<Pair<Integer, String>> result = new ArrayList<>();
        ArrayList<ProjectListData> projectLists = getProjectLists(projectId);
        if (projectLists != null && !projectLists.isEmpty()) {
            for (ProjectListData pl : projectLists) {
                if (pl != null) {
                    result.add(new Pair<>(pl.type, pl.name));
                }
            }
        }
        if (result.isEmpty()) {
            String clean = getCleanPageName(str);
            if (mapLists.containsKey(clean)) {
                result = (ArrayList) mapLists.get(clean);
            }
        }
        return result;
    }

    public static ArrayList<Pair<Integer, String>> getLists(String str) {
        return getLists(str, "");
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
                int typeVal = ((Integer) pair.first).intValue();
                if (typeVal == i || typeVal == (i + 4)) {
                    arrayList.add(pair.second);
                }
            }
        }
        return arrayList;
    }

    public static void initMaps() {
        if (mapBlocks != null) {
            mapBlocks.clear();
        }
        if (mapVariables != null) {
            mapVariables.clear();
        }
        if (mapLists != null) {
            mapLists.clear();
        }
        mapBlocks = new HashMap();
        mapVariables = new HashMap();
        mapLists = new HashMap();
        mapFunctions = new HashMap();
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
        if (str2 == null || str2.isEmpty()) return false;
        HashMap<String, ArrayList<BlockBean>> map = mapBlocks.get(str);
        if (map == null) {
            return false;
        }
        for (Entry<String, ArrayList<BlockBean>> entry : map.entrySet()) {
            ArrayList<BlockBean> blockList = entry.getValue();
            if (blockList != null) {
                for (BlockBean bean : blockList) {
                    if (bean == null) continue;
                    if ("getListVar".equals(bean.opCode) && str2.equals(bean.spec)) {
                        return true;
                    }
                    if (bean.parameters != null) {
                        for (String param : bean.parameters) {
                            if (str2.equals(param)) return true;
                        }
                    }
                    String op = bean.opCode != null ? bean.opCode : "";
                    if (op.startsWith("jsList") || op.equals("jsSetupList") || op.contains("List")) {
                        if (bean.spec != null && bean.spec.contains(str2)) return true;
                    }
                }
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
                        case VAR_TYPE_BOOLEAN:
                            if (blockBean.spec != null && blockBean.spec.equals(str2)) {
                                return true;
                            }
                            if (blockBean.parameters != null && !blockBean.parameters.isEmpty() && blockBean.parameters.get(0) != null) {
                                if (blockBean.parameters.get(0).equals(str2)) {
                                    return true;
                                }
                            }
                            break;
                        case VAR_TYPE_INT:
                        case VAR_TYPE_STRING:
                        case 3:
                        case 4:
                        case 5:
                            if (blockBean.parameters != null && !blockBean.parameters.isEmpty() && blockBean.parameters.get(VAR_TYPE_BOOLEAN) != null) {
                                if (blockBean.parameters.get(VAR_TYPE_BOOLEAN).equals(str2)) {
                                    return true;
                                }
                            }
                            break;
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
        public ArrayList<String> components = new ArrayList<>();
        public ArrayList<String> events = new ArrayList<>();
    }

    public static String serializePageLogicData(PageLogicData data, String pageName) {
        StringBuilder sb = new StringBuilder();
        
        // _var
        sb.append("@").append(pageName).append("_var\n");
        if (data.lists != null) {
            for (Pair<Integer, String> p : data.lists) {
                sb.append(p.first).append(":").append(p.second).append("\n");
            }
        }
        if (data.variables != null) {
            for (Pair<Integer, String> p : data.variables) {
                sb.append(p.first).append(":").append(p.second).append("\n");
            }
        }
        sb.append("\n");
        
        // _func
        sb.append("@").append(pageName).append("_func\n");
        if (data.functions != null) {
            for (Pair<String, String> p : data.functions) {
                sb.append(p.first).append(":").append(p.second).append("\n");
            }
        }
        sb.append("\n");
        
        // _components
        sb.append("@").append(pageName).append("_components\n");
        if (data.components != null) {
            for (String c : data.components) {
                sb.append(c).append("\n");
            }
        }
        sb.append("\n");
        
        // _events
        sb.append("@").append(pageName).append("_events\n");
        if (data.events != null) {
            for (String e : data.events) {
                sb.append(e).append("\n");
            }
        }
        sb.append("\n");
        
        // _blocks
        sb.append("@").append(pageName).append("_blocks\n");
        Gson gson = new Gson();
        if (data.blocks != null) {
            for (Map.Entry<String, ArrayList<BlockBean>> entry : data.blocks.entrySet()) {
                if (entry.getValue() != null) {
                    for (BlockBean b : entry.getValue()) {
                        sb.append(gson.toJson(b)).append("\n");
                    }
                }
            }
        }
        sb.append("\n");
        
        // _workspace
        sb.append("@").append(pageName).append("_workspace\n");
        if (data.blocks != null) {
            for (Map.Entry<String, ArrayList<BlockBean>> entry : data.blocks.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    sb.append(entry.getKey()).append(":");
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(entry.getValue().get(i).id);
                    }
                    sb.append("\n");
                }
            }
        }
        
        return sb.toString();
    }

    public static PageLogicData deserializePageLogicData(String text, String pageName) {
        PageLogicData data = new PageLogicData();
        if (text == null || text.trim().isEmpty()) {
            return data;
        }
        
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            try {
                Gson gson = new Gson();
                com.google.gson.reflect.TypeToken<Map<String, Object>> rawType =
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>(){};
                Map<String, Object> rawMap = gson.fromJson(trimmed, rawType.getType());

                PageLogicData result = new PageLogicData();

                Object blocksObj = rawMap.get("blocks");
                if (blocksObj instanceof Map) {
                    com.google.gson.reflect.TypeToken<ArrayList<BlockBean>> beanListType =
                        new com.google.gson.reflect.TypeToken<ArrayList<BlockBean>>(){};
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) blocksObj).entrySet()) {
                        String key = entry.getKey().toString();
                        Object val = entry.getValue();
                        if (val != null) {
                            try {
                                String valJson = gson.toJson(val);
                                ArrayList<BlockBean> beans = gson.fromJson(valJson, beanListType.getType());
                                if (beans != null) {
                                    result.blocks.put(key, beans);
                                }
                            } catch (Exception ignored2) {}
                        }
                    }
                }

                try {
                    com.google.gson.reflect.TypeToken<ArrayList<android.util.Pair<Integer, String>>> pairsType =
                        new com.google.gson.reflect.TypeToken<ArrayList<android.util.Pair<Integer, String>>>(){};
                    Object varsObj = rawMap.get("variables");
                    if (varsObj != null) {
                        String varsJson = gson.toJson(varsObj);
                        ArrayList<android.util.Pair<Integer, String>> vars = gson.fromJson(varsJson, pairsType.getType());
                        if (vars != null) result.variables = vars;
                    }
                    Object listsObj = rawMap.get("lists");
                    if (listsObj != null) {
                        String listsJson = gson.toJson(listsObj);
                        ArrayList<android.util.Pair<Integer, String>> lists = gson.fromJson(listsJson, pairsType.getType());
                        if (lists != null) result.lists = lists;
                    }
                } catch (Exception ignored3) {}

                if (!result.blocks.isEmpty()) {
                    if (result.blocks.containsKey("onCreate_initializeLogic") && !result.blocks.containsKey("onPageLoad_onPageLoad")) {
                        result.blocks.put("onPageLoad_onPageLoad", result.blocks.get("onCreate_initializeLogic"));
                    } else if (result.blocks.containsKey("onPageLoad_onPageLoad") && !result.blocks.containsKey("onCreate_initializeLogic")) {
                        result.blocks.put("onCreate_initializeLogic", result.blocks.get("onPageLoad_onPageLoad"));
                    } else if (pageName != null && !pageName.isEmpty()) {
                        String clean = getCleanPageName(pageName);
                        if (result.blocks.containsKey(clean)) {
                            result.blocks.put("onCreate_initializeLogic", result.blocks.get(clean));
                            result.blocks.put("onPageLoad_onPageLoad", result.blocks.get(clean));
                        } else if (result.blocks.containsKey(pageName)) {
                            result.blocks.put("onCreate_initializeLogic", result.blocks.get(pageName));
                            result.blocks.put("onPageLoad_onPageLoad", result.blocks.get(pageName));
                        }
                    }
                    return result;
                }
            } catch (Exception ignored) {}
            return data;
        }

        if (trimmed.startsWith("[")) {
            String eventKey = (pageName != null && !pageName.isEmpty()) ? pageName : "onCreate_initializeLogic";
            try {
                Gson gson = new Gson();
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BlockBean>>(){}.getType();
                ArrayList<BlockBean> blockList = gson.fromJson(trimmed, listType);
                if (blockList != null && !blockList.isEmpty() && blockList.get(0) != null && blockList.get(0).opCode != null) {
                    data.blocks.put("onCreate_initializeLogic", blockList);
                    data.blocks.put("onPageLoad_onPageLoad", blockList);
                    if (eventKey != null && !eventKey.isEmpty()) {
                        data.blocks.put(eventKey, blockList);
                        data.blocks.put(getCleanPageName(eventKey), blockList);
                    }
                    return data;
                }
            } catch (Exception ignored) {}

            try {
                Gson gson = new Gson();
                java.lang.reflect.Type rawListType = new com.google.gson.reflect.TypeToken<ArrayList<Map<String, Object>>>(){}.getType();
                ArrayList<Map<String, Object>> rawMaps = gson.fromJson(trimmed, rawListType);
                if (rawMaps != null && !rawMaps.isEmpty()) {
                    HtmlCssImporter importer = new HtmlCssImporter(SketchApplication.getContext());
                    ArrayList<BlockBean> convertedBeans = importer.convertRawMapsToBeans(rawMaps);
                    if (convertedBeans != null && !convertedBeans.isEmpty()) {
                        data.blocks.put("onCreate_initializeLogic", convertedBeans);
                        data.blocks.put("onPageLoad_onPageLoad", convertedBeans);
                        if (eventKey != null && !eventKey.isEmpty()) {
                            data.blocks.put(eventKey, convertedBeans);
                            data.blocks.put(getCleanPageName(eventKey), convertedBeans);
                        }
                        return data;
                    }
                }
            } catch (Exception ignored) {}

            return data;
        }
        
        String[] lines = text.split("\n");
        String currentSection = "";
        
        Map<String, BlockBean> allBlocksMap = new HashMap<>();
        Map<String, List<String>> workspaceMap = new HashMap<>();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;
            
            if (trimmedLine.startsWith("@")) {
                currentSection = trimmedLine;
                continue;
            }
            
            if (currentSection.endsWith("_var")) {
                int colonIdx = trimmedLine.indexOf(":");
                if (colonIdx > 0) {
                    try {
                        int type = Integer.parseInt(trimmedLine.substring(0, colonIdx));
                        String name = trimmedLine.substring(colonIdx + 1);
                        data.variables.add(new Pair<>(type, name));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (currentSection.endsWith("_func")) {
                int colonIdx = trimmedLine.indexOf(":");
                if (colonIdx > 0) {
                    String name = trimmedLine.substring(0, colonIdx);
                    String spec = trimmedLine.substring(colonIdx + 1);
                    data.functions.add(new Pair<>(name, spec));
                }
            } else if (currentSection.endsWith("_components")) {
                data.components.add(trimmedLine);
            } else if (currentSection.endsWith("_events")) {
                data.events.add(trimmedLine);
            } else if (currentSection.endsWith("_blocks")) {
                try {
                    BlockBean b = new Gson().fromJson(trimmedLine, BlockBean.class);
                    if (b != null && b.id != null) {
                        allBlocksMap.put(b.id, b);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (currentSection.endsWith("_workspace")) {
                int colonIdx = trimmedLine.indexOf(":");
                if (colonIdx > 0) {
                    String eventKey = trimmedLine.substring(0, colonIdx);
                    String[] ids = trimmedLine.substring(colonIdx + 1).split(",");
                    List<String> idList = new ArrayList<>();
                    for (String id : ids) {
                        if (!id.trim().isEmpty()) {
                            idList.add(id.trim());
                        }
                    }
                    workspaceMap.put(eventKey, idList);
                }
            }
        }
        
        for (Map.Entry<String, List<String>> entry : workspaceMap.entrySet()) {
            ArrayList<BlockBean> eventBlocks = new ArrayList<>();
            for (String id : entry.getValue()) {
                BlockBean b = allBlocksMap.get(id);
                if (b != null) {
                    eventBlocks.add(b);
                }
            }
            data.blocks.put(entry.getKey(), eventBlocks);
        }

        if (data.blocks.containsKey("onPageLoad_onPageLoad") && !data.blocks.containsKey("onCreate_initializeLogic")) {
            data.blocks.put("onCreate_initializeLogic", data.blocks.get("onPageLoad_onPageLoad"));
        } else if (data.blocks.containsKey("onCreate_initializeLogic") && !data.blocks.containsKey("onPageLoad_onPageLoad")) {
            data.blocks.put("onPageLoad_onPageLoad", data.blocks.get("onCreate_initializeLogic"));
        } else if (pageName != null && !pageName.isEmpty()) {
            String clean = getCleanPageName(pageName);
            if (data.blocks.containsKey(clean)) {
                data.blocks.put("onCreate_initializeLogic", data.blocks.get(clean));
                data.blocks.put("onPageLoad_onPageLoad", data.blocks.get(clean));
            } else if (data.blocks.containsKey(pageName)) {
                data.blocks.put("onCreate_initializeLogic", data.blocks.get(pageName));
                data.blocks.put("onPageLoad_onPageLoad", data.blocks.get(pageName));
            }
        }
        
        return data;
    }

    public static void loadSavedLogic(Context context, String projectId, String pageName) {
        if (projectId == null || projectId.isEmpty()) projectId = currentProjectId;
        if (projectId == null || projectId.isEmpty()) projectId = "default_project";
        if (pageName == null || pageName.isEmpty()) pageName = currentPageName;
        if (pageName == null || pageName.isEmpty()) pageName = "index";
        String cleanPage = getCleanPageName(pageName);

        File projDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
        File logicFile = new File(projDir, "logic");

        if (FileUtil.exists(logicFile)) {
            loadSavedLogicFromUnifiedFile(context, projectId, logicFile);
        } else {
            migrateLegacyLogicFiles(context, projectId);
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
    }

    public static synchronized void loadSavedLogicFromUnifiedFile(Context context, String projectId, File logicFile) {
        if (!FileUtil.exists(logicFile)) return;
        String content = FileUtil.readFile(logicFile.getAbsolutePath());
        if (content == null || content.trim().isEmpty()) return;

        String[] lines = content.split("\r?\n");
        String currentSection = null;
        List<String> sectionLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                if (currentSection != null && !sectionLines.isEmpty()) {
                    processLogicSection(currentSection, sectionLines);
                    sectionLines.clear();
                }
                currentSection = trimmed.substring(1).trim();
            } else if (currentSection != null && !trimmed.isEmpty()) {
                sectionLines.add(trimmed);
            }
        }
        if (currentSection != null && !sectionLines.isEmpty()) {
            processLogicSection(currentSection, sectionLines);
        }
    }

    private static void processLogicSection(String sectionHeader, List<String> lines) {
        if (sectionHeader == null || lines == null || lines.isEmpty()) return;
        Gson gson = new Gson();

        if (sectionHeader.endsWith("_var")) {
            String target = sectionHeader.substring(0, sectionHeader.length() - 4);
            String cleanTarget = getCleanPageName(target);
            if (!mapVariables.containsKey(cleanTarget)) mapVariables.put(cleanTarget, new ArrayList<>());
            ArrayList<Pair<Integer, String>> varList = mapVariables.get(cleanTarget);

            for (String line : lines) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String typePart = line.substring(0, colonIdx).trim();
                    String varName = line.substring(colonIdx + 1).trim();
                    try {
                        int finalType;
                        if (typePart.contains("-")) {
                            String[] parts = typePart.split("-");
                            int decl = Integer.parseInt(parts[0].trim()); // 0 = const, 1 = let
                            int vType = Integer.parseInt(parts[1].trim()); // 0=bool, 1=num, 2=str
                            finalType = (decl == 0) ? (vType + 4) : vType;
                        } else {
                            finalType = Integer.parseInt(typePart);
                        }
                        boolean exists = false;
                        for (Pair<Integer, String> existing : varList) {
                            if (existing.second != null && existing.second.equals(varName)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            varList.add(new Pair<>(finalType, varName));
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (!cleanTarget.equals(target)) {
                mapVariables.put(target, varList);
            }
        } else if (sectionHeader.endsWith("_moreblock")) {
            String target = sectionHeader.substring(0, sectionHeader.length() - 10);
            String cleanTarget = getCleanPageName(target);
            if (!mapFunctions.containsKey(cleanTarget)) mapFunctions.put(cleanTarget, new ArrayList<>());
            ArrayList<Pair<String, String>> funcList = mapFunctions.get(cleanTarget);

            for (String line : lines) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String funcName = line.substring(0, colonIdx).trim();
                    String spec = line.substring(colonIdx + 1).trim();
                    boolean exists = false;
                    for (Pair<String, String> existing : funcList) {
                        if (existing.first != null && existing.first.equals(funcName)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        funcList.add(new Pair<>(funcName, spec));
                    }
                }
            }
            if (!cleanTarget.equals(target)) {
                mapFunctions.put(target, funcList);
            }
        } else {
            // Block section: could be @target or @target_eventKey or @target_func_name
            String target;
            String eventKey;
            if (sectionHeader.contains("_func_")) {
                int idx = sectionHeader.indexOf("_func_");
                target = sectionHeader.substring(0, idx);
                eventKey = sectionHeader.substring(idx + 1); // "func_xxx"
            } else if (sectionHeader.contains("_") && !sectionHeader.equals("css_style") && !sectionHeader.equals("js_script")) {
                int idx = sectionHeader.indexOf('_');
                target = sectionHeader.substring(0, idx);
                eventKey = sectionHeader.substring(idx + 1);
            } else {
                target = sectionHeader;
                eventKey = "onCreate_initializeLogic";
            }

            String cleanTarget = getCleanPageName(target);
            if (!mapBlocks.containsKey(cleanTarget)) mapBlocks.put(cleanTarget, new HashMap<>());
            HashMap<String, ArrayList<BlockBean>> pageMap = mapBlocks.get(cleanTarget);

            ArrayList<BlockBean> blocks = new ArrayList<>();
            for (String line : lines) {
                try {
                    BlockBean b = gson.fromJson(line, BlockBean.class);
                    if (b != null && b.opCode != null) {
                        blocks.add(b);
                    }
                } catch (Exception ignored) {}
            }

            pageMap.put(eventKey, blocks);
            if ("onCreate_initializeLogic".equals(eventKey)) {
                pageMap.put("immediate", blocks);
                pageMap.put(cleanTarget, blocks);
            }
            if (!cleanTarget.equals(target)) {
                if (!mapBlocks.containsKey(target)) mapBlocks.put(target, new HashMap<>());
                mapBlocks.get(target).put(eventKey, blocks);
            }
        }
    }

    private static void migrateLegacyLogicFiles(Context context, String projectId) {
        File projDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
        if (!projDir.exists() || !projDir.isDirectory()) return;

        File[] files = projDir.listFiles();
        if (files == null) return;

        boolean found = false;
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith("_logic.json") || "style_logic.json".equals(name)) {
                found = true;
                String cleanPage = name.replace("_logic.json", "").replace(".json", "");
                if ("style_logic".equals(cleanPage) || "style".equals(cleanPage)) cleanPage = "css_style";
                String content = FileUtil.readFile(f.getAbsolutePath());
                if (content != null && !content.trim().isEmpty() && !content.trim().equals("{}") && !content.trim().equals("[]")) {
                    try {
                        PageLogicData data = deserializePageLogicData(content, cleanPage);
                        if (data != null) {
                            if (data.blocks != null && !data.blocks.isEmpty()) {
                                if (!mapBlocks.containsKey(cleanPage)) mapBlocks.put(cleanPage, new HashMap<>());
                                mapBlocks.get(cleanPage).putAll(data.blocks);
                            }
                            if (data.variables != null && !data.variables.isEmpty()) {
                                if (!mapVariables.containsKey(cleanPage)) mapVariables.put(cleanPage, new ArrayList<>());
                                mapVariables.get(cleanPage).addAll(data.variables);
                            }
                            if (data.functions != null && !data.functions.isEmpty()) {
                                if (!mapFunctions.containsKey(cleanPage)) mapFunctions.put(cleanPage, new ArrayList<>());
                                mapFunctions.get(cleanPage).addAll(data.functions);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (found && ((mapBlocks != null && !mapBlocks.isEmpty()) || (mapVariables != null && !mapVariables.isEmpty()))) {
            saveAllSavedLogic(context, projectId);
        }
    }

    public static void loadSavedLogic() {
        loadSavedLogic(null, currentProjectId, currentPageName);
    }

    public static void saveSavedLogic(Context context, String projectId, String pageName) {
        saveAllSavedLogic(context, projectId);
    }

    public static synchronized void saveAllSavedLogic(Context context, String projectId) {
        if (projectId == null || projectId.isEmpty()) projectId = currentProjectId;
        if (projectId == null || projectId.isEmpty()) return;

        File projDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
        if (!projDir.exists()) projDir.mkdirs();

        File logicFile = new File(projDir, "logic");

        StringBuilder sb = new StringBuilder();
        Gson gson = new Gson();

        java.util.Set<String> allTargets = new java.util.LinkedHashSet<>();
        allTargets.add("script.js");
        allTargets.add("styles.css");

        if (mapBlocks != null) {
            for (String key : mapBlocks.keySet()) {
                String canonical = getCanonicalTargetName(key);
                if (!canonical.endsWith(".html") && !canonical.endsWith(".htm")) {
                    allTargets.add(canonical);
                }
            }
        }
        if (mapVariables != null) {
            for (String key : mapVariables.keySet()) {
                String canonical = getCanonicalTargetName(key);
                if (!canonical.endsWith(".html") && !canonical.endsWith(".htm")) {
                    allTargets.add(canonical);
                }
            }
        }
        if (mapFunctions != null) {
            for (String key : mapFunctions.keySet()) {
                String canonical = getCanonicalTargetName(key);
                if (!canonical.endsWith(".html") && !canonical.endsWith(".htm")) {
                    allTargets.add(canonical);
                }
            }
        }

        for (String target : allTargets) {
            String cleanTarget = getCleanPageName(target);

            // 1. Blocks
            HashMap<String, ArrayList<BlockBean>> blocksForTarget = null;
            if (mapBlocks != null) {
                blocksForTarget = mapBlocks.get(cleanTarget);
                if (blocksForTarget == null) blocksForTarget = mapBlocks.get(target);
            }

            ArrayList<BlockBean> defaultBlocks = null;
            if (blocksForTarget != null) {
                defaultBlocks = blocksForTarget.get("onCreate_initializeLogic");
                if (defaultBlocks == null || defaultBlocks.isEmpty()) {
                    defaultBlocks = blocksForTarget.get("immediate");
                }
                if (defaultBlocks == null || defaultBlocks.isEmpty()) {
                    defaultBlocks = blocksForTarget.get(cleanTarget);
                }
            }

            sb.append("@").append(target).append("\n");
            if (defaultBlocks != null) {
                for (BlockBean b : defaultBlocks) {
                    sb.append(gson.toJson(b)).append("\n");
                }
            }
            sb.append("\n");

            // Event blocks
            if (blocksForTarget != null) {
                for (Map.Entry<String, ArrayList<BlockBean>> entry : blocksForTarget.entrySet()) {
                    String eventKey = entry.getKey();
                    if ("onCreate_initializeLogic".equals(eventKey) || "onPageLoad_onPageLoad".equals(eventKey)
                            || "immediate".equals(eventKey) || cleanTarget.equals(eventKey)
                            || eventKey.startsWith("func_")) {
                        continue;
                    }
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        sb.append("@").append(target).append("_").append(eventKey).append("\n");
                        for (BlockBean b : entry.getValue()) {
                            sb.append(gson.toJson(b)).append("\n");
                        }
                        sb.append("\n");
                    }
                }
            }

            // 2. Variables (_var)
            ArrayList<Pair<Integer, String>> vars = null;
            if (mapVariables != null) {
                vars = mapVariables.get(cleanTarget);
                if (vars == null) vars = mapVariables.get(target);
            }

            sb.append("@").append(target).append("_var\n");
            if (vars != null) {
                for (Pair<Integer, String> p : vars) {
                    if (p != null && p.second != null) {
                        int typeVal = p.first != null ? p.first : 2;
                        int declType = (typeVal >= 4) ? 0 : 1; // 0=const, 1=let
                        int varType = (typeVal >= 4) ? (typeVal - 4) : typeVal; // 0=bool, 1=num, 2=str
                        sb.append(declType).append("-").append(varType).append(":").append(p.second).append("\n");
                    }
                }
            }
            sb.append("\n");

            // 3. MoreBlocks (_moreblock)
            ArrayList<Pair<String, String>> funcs = null;
            if (mapFunctions != null) {
                funcs = mapFunctions.get(cleanTarget);
                if (funcs == null) funcs = mapFunctions.get(target);
            }

            sb.append("@").append(target).append("_moreblock\n");
            if (funcs != null) {
                for (Pair<String, String> f : funcs) {
                    if (f != null && f.first != null) {
                        sb.append(f.first).append(":").append(f.second != null ? f.second : "").append("\n");
                    }
                }
            }
            sb.append("\n");

            // Function body blocks
            if (blocksForTarget != null) {
                for (Map.Entry<String, ArrayList<BlockBean>> entry : blocksForTarget.entrySet()) {
                    String eventKey = entry.getKey();
                    if (eventKey.startsWith("func_") && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        sb.append("@").append(target).append("_").append(eventKey).append("\n");
                        for (BlockBean b : entry.getValue()) {
                            sb.append(gson.toJson(b)).append("\n");
                        }
                        sb.append("\n");
                    }
                }
            }
        }

        FileUtil.writeFile(logicFile.getAbsolutePath(), sb.toString());
        cleanupLegacyLogicFiles(context, projectId);
    }

    public static void cleanupLegacyLogicFiles(Context context, String projectId) {
        try {
            File projDir = new File(FileUtil.getDragWebDir(context), "projects/" + projectId);
            if (!projDir.exists() || !projDir.isDirectory()) return;
            File[] files = projDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith("_logic.json") || "style_logic.json".equals(f.getName())) {
                        f.delete();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static String getCanonicalTargetName(String key) {
        if (key == null || key.isEmpty()) return "index.html";
        String clean = getCleanPageName(key);
        if ("script".equals(clean)) return "script.js";
        if ("css_style".equals(clean) || "style".equals(clean)) return "styles.css";
        if (clean.endsWith(".html")) return clean;
        return clean + ".html";
    }

    public static void removePage(Context context, String projectId, String pageName) {
        String clean = getCleanPageName(pageName);
        if (mapBlocks != null) {
            mapBlocks.remove(clean);
            mapBlocks.remove(pageName);
        }
        if (mapVariables != null) {
            mapVariables.remove(clean);
            mapVariables.remove(pageName);
        }
        if (mapFunctions != null) {
            mapFunctions.remove(clean);
            mapFunctions.remove(pageName);
        }
        saveAllSavedLogic(context, projectId);
    }

    public static void renamePage(Context context, String projectId, String oldName, String newName) {
        String oldClean = getCleanPageName(oldName);
        String newClean = getCleanPageName(newName);
        if (mapBlocks != null && mapBlocks.containsKey(oldClean)) {
            mapBlocks.put(newClean, mapBlocks.remove(oldClean));
        }
        if (mapVariables != null && mapVariables.containsKey(oldClean)) {
            mapVariables.put(newClean, mapVariables.remove(oldClean));
        }
        if (mapFunctions != null && mapFunctions.containsKey(oldClean)) {
            mapFunctions.put(newClean, mapFunctions.remove(oldClean));
        }
        saveAllSavedLogic(context, projectId);
    }

    public static void saveSavedLogic(String filename) {
        saveSavedLogic(null, currentProjectId, filename);
    }

    public static String compileFileSource(Context context, String projectId, String pageName) {
        return ProjectCodeGenerator.compileJsForFile(context, projectId, getCleanPageName(pageName));
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
        String clean = getCleanPageName(str);
        if (mapFunctions.containsKey(clean)) {
            ArrayList arrayList = (ArrayList) mapFunctions.get(clean);
            if (arrayList != null) {
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    Pair pair = (Pair) it.next();
                    if (((String) pair.first).equals(str2)) {
                        it.remove();
                        break;
                    }
                }
                if (mapBlocks.containsKey(clean) && ((HashMap) mapBlocks.get(clean)).containsKey(str2 + "_" + "moreBlock")) {
                    ((HashMap) mapBlocks.get(clean)).remove(str2 + "_" + "moreBlock");
                }
            }
        }
    }

    public static void removeList(String str, String str2) {
        String clean = getCleanPageName(str);
        if (mapLists.containsKey(clean)) {
            ArrayList arrayList = (ArrayList) mapLists.get(clean);
            if (arrayList != null) {
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    Pair pair = (Pair) it.next();
                    if (((String) pair.second).equals(str2)) {
                        it.remove();
                        return;
                    }
                }
            }
        }
    }

    public static void removeVariable(String str, String str2) {
        String clean = getCleanPageName(str);
        if (mapVariables.containsKey(clean)) {
            ArrayList arrayList = (ArrayList) mapVariables.get(clean);
            if (arrayList != null) {
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    Pair pair = (Pair) it.next();
                    if (((String) pair.second).equals(str2)) {
                        it.remove();
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
        HashMap<String, ArrayList<BlockBean>> pageMap = mapBlocks.get(clean);
        // Normalize page load events to onPageLoad_onPageLoad and replicate aliases
        if ("onPageLoad_onPageLoad".equals(str2) || "onCreate_initializeLogic".equals(str2) || "initializeLogic_initializeLogic".equals(str2) || "onPageLoad".equals(str2) || "initializeLogic".equals(str2)) {
            pageMap.remove("onCreate_initializeLogic");
            pageMap.remove("onPageLoad_onPageLoad");
            pageMap.remove("initializeLogic_initializeLogic");
            pageMap.remove(clean);
            pageMap.remove(str);
            ArrayList<BlockBean> safeList = arrayList != null ? arrayList : new ArrayList<>();
            pageMap.put("onPageLoad_onPageLoad", safeList);
            pageMap.put("onCreate_initializeLogic", safeList);
            return;
        }
        pageMap.put(str2, arrayList != null ? arrayList : new ArrayList<>());
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
        File dragWebDir = FileUtil.getDragWebDir(context);
        java.io.File file = new java.io.File(dragWebDir, "projects/" + projectId + "/pages/" + cleanPage + ".json");
        if (!FileUtil.exists(file) && "index".equals(cleanPage)) {
            file = new java.io.File(dragWebDir, "projects/" + projectId + "/layout.json");
        }
        if (!FileUtil.exists(file)) {
            file = new java.io.File(dragWebDir, "projects/" + projectId + "_" + cleanPage + ".json");
        }

        if (FileUtil.exists(file)) {
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
