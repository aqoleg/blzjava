// thin wrapper for the bluzelle client
// usage:
//    Wrapper wrapper = new Wrapper();
//    wrapper.connect(mnemonicString, endpointString, uuidString, chainIdString);
//    String result = wrapper.request(requestString);
// requests examples:
//    {"method":"connect","args":["mnemonic words","localhost:5000","uuid","bluzelle"]}
//    {"method":"connect","args":["mnemonic words"]}
//    {"method":"create","args":["key","value"]}
//    {method:create,args:[key,value,{gas_price:10,max_gas:10,max_fee:10}]}
//    {"method":"create","args":["key","value",{"gas_price":10},{"days":10,"hours":10,"minutes":10,"seconds":10}]}
//    {"method":"deleteAll"}
//    {"method":"delete_all","args":[{"max_gas":10000}]}
package space.aqoleg.server;

import space.aqoleg.bluzelle.Bluzelle;
import space.aqoleg.bluzelle.Connection;
import space.aqoleg.bluzelle.GasInfo;
import space.aqoleg.bluzelle.LeaseInfo;
import space.aqoleg.json.JsonArray;
import space.aqoleg.json.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class Wrapper {
    private static final GasInfo gasInfo = new GasInfo(1000, 0, 0);
    private Bluzelle bluzelle;

    /**
     * creates and configures connection
     *
     * @param mnemonic mnemonic of the private key for account
     * @param endpoint hostname and port of rest server or null for default "http://localhost:1317"
     * @param chainId  chain id of account or null for default "bluzelle"
     * @param uuid     uuid or null for the same as address
     * @throws NullPointerException           if mnemonic == null
     * @throws Connection.ConnectionException if can not connect to the node
     */
    public void connect(String mnemonic, String endpoint, String uuid, String chainId) {
        bluzelle = Bluzelle.connect(mnemonic, endpoint, uuid, chainId);
    }

    /**
     * @param request String with request
     * @return String result or null
     * @throws UnsupportedOperationException  if bluzelle is not connected or method is unknown
     * @throws Connection.ConnectionException if can not connect to the node
     * @throws Bluzelle.ServerException       if server returns error
     */
    public String request(String request) {
        JsonObject json = JsonObject.parse(request);
        String method = json.getString("method");
        JsonArray args = json.getArray("args");

        if (method.equals("connect")) {
            connect(
                    args.getString(0),
                    (args.length() > 1) ? args.getString(1) : null,
                    (args.length() > 2) ? args.getString(2) : null,
                    (args.length() > 3) ? args.getString(3) : null
            );
            return null;
        } else if (bluzelle == null) {
            throw new UnsupportedOperationException("bluzelle is not connected");
        }

        switch (method) {
            case "version":
                return bluzelle.version();
            case "account":
                return bluzelle.account().toString();
            case "create":
                bluzelle.create(
                        args.getString(0),
                        args.getString(1),
                        getGasInfo(args, 2),
                        getLeaseInfo(args, 3)
                );
                return null;
            case "read":
                return bluzelle.read(
                        args.getString(0),
                        (args.length() > 1) && args.getBoolean(1)
                );
            case "txRead":
            case "tx_read":
                return bluzelle.txRead(args.getString(0), getGasInfo(args, 1));
            case "update":
                bluzelle.update(
                        args.getString(0),
                        args.getString(1),
                        getGasInfo(args, 2),
                        getLeaseInfo(args, 3)
                );
                return null;
            case "delete":
                bluzelle.delete(args.getString(0), getGasInfo(args, 1));
                return null;
            case "has":
                return bluzelle.has(args.getString(0)) ? "true" : "false";
            case "txHas":
            case "tx_has":
                return bluzelle.txHas(args.getString(0), getGasInfo(args, 1)) ? "true" : "false";
            case "keys":
                return listToJson(bluzelle.keys());
            case "txKeys":
            case "tx_keys":
                return listToJson(bluzelle.txKeys(getGasInfo(args, 0)));
            case "rename":
                bluzelle.rename(args.getString(0), args.getString(1), getGasInfo(args, 2));
                return null;
            case "count":
                return String.valueOf(bluzelle.count());
            case "txCount":
            case "tx_count":
                return String.valueOf(bluzelle.txCount(getGasInfo(args, 0)));
            case "deleteAll":
            case "delete_all":
                bluzelle.deleteAll(getGasInfo(args, 0));
                return null;
            case "keyValues":
            case "key_values":
                return mapToJson(bluzelle.keyValues());
            case "txKeyValues":
            case "tx_key_values":
                return mapToJson(bluzelle.txKeyValues(getGasInfo(args, 0)));
            case "multiUpdate":
            case "multi_update":
                bluzelle.multiUpdate(jsonToMap(args.getArray(0)), getGasInfo(args, 1));
                return null;
            case "getLease":
            case "get_lease":
                return String.valueOf(bluzelle.getLease(args.getString(0)));
            case "txGetLease":
            case "tx_get_lease":
                return String.valueOf(bluzelle.txGetLease(args.getString(0), getGasInfo(args, 1)));
            case "renewLease":
            case "renew_lease":
                bluzelle.renewLease(args.getString(0), getGasInfo(args, 1), getLeaseInfo(args, 2));
                return null;
            case "renewLeaseAll":
            case "renew_lease_all":
                bluzelle.renewLeaseAll(getGasInfo(args, 0), getLeaseInfo(args, 1));
                return null;
            case "getNShortestLeases":
            case "get_n_shortest_leases":
                return mapToLeases(bluzelle.getNShortestLeases(args.getInteger(0)));
            case "txGetNShortestLeases":
            case "tx_get_n_shortest_leases":
                return mapToLeases(bluzelle.txGetNShortestLeases(args.getInteger(0), getGasInfo(args, 1)));
            default:
                throw new UnsupportedOperationException("unknown method \"" + method + "\"");
        }
    }

    private static GasInfo getGasInfo(JsonArray array, int index) {
        if (array == null || index >= array.length()) {
            return gasInfo;
        }
        JsonObject json = array.getObject(index);
        Integer gasPrice = json.getInteger("gas_price");
        Integer maxGas = json.getInteger("max_gas");
        Integer maxFee = json.getInteger("max_fee");
        return new GasInfo(
                gasPrice == null ? 0 : gasPrice,
                maxGas == null ? 0 : maxGas,
                maxFee == null ? 0 : maxFee
        );
    }

    private static LeaseInfo getLeaseInfo(JsonArray array, int index) {
        if (array == null || index >= array.length()) {
            return null;
        }
        JsonObject json = array.getObject(index);
        Integer days = json.getInteger("days");
        Integer hours = json.getInteger("hours");
        Integer minutes = json.getInteger("minutes");
        Integer seconds = json.getInteger("seconds");
        return new LeaseInfo(
                days == null ? 0 : days,
                hours == null ? 0 : hours,
                minutes == null ? 0 : minutes,
                seconds == null ? 0 : seconds
        );
    }

    private static String listToJson(ArrayList<String> list) {
        JsonArray array = new JsonArray();
        for (String s : list) {
            array.put(s);
        }
        return array.toString();
    }

    private static String mapToJson(HashMap<String, String> map) {
        JsonArray array = new JsonArray();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            JsonObject object = new JsonObject();
            object.put("key", entry.getKey());
            object.put("value", entry.getValue());
            array.put(object);
        }
        return array.toString();
    }

    private static HashMap<String, String> jsonToMap(JsonArray json) {
        HashMap<String, String> map = new HashMap<>();
        JsonObject object;
        int length = json.length();
        for (int i = 0; i < length; i++) {
            object = json.getObject(i);
            map.put(object.getString("key"), object.getString("value"));
        }
        return map;
    }

    private static String mapToLeases(HashMap<String, Integer> map) {
        JsonArray array = new JsonArray();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            JsonObject object = new JsonObject();
            object.put("key", entry.getKey());
            object.put("lease", entry.getValue());
            array.put(object);
        }
        return array.toString();
    }
}