package fr.reloaded.ovh;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class OVHStop extends JavaPlugin {

    private final static String LOG_PREFIX = "[Reloaded-MC - OVH-Stop] ";
    private String applicationKey, applicationSecret, consumerKey, projectId;


    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.applicationKey = this.getConfig().getString("OVH_APPLICATION_KEY");
        this.applicationSecret = this.getConfig().getString("OVH_APPLICATION_SECRET");
        this.consumerKey = this.getConfig().getString("OVH_CONSUMER_KEY");
        this.projectId = this.getConfig().getString("OVH_PROJECT_ID");
    }

    @Override
    public void onDisable() {
        try {
            Map<String, String> serverList = getInstancesList();
            for (Map.Entry<String, String> entry : serverList.entrySet()) {
                if (entry.getValue().equals(this.getPublicAddress())) {
                    this.stopServer(entry.getKey());
                    System.out.println(LOG_PREFIX + "Successfully deleted this server from OVH.");
                    break;
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop the server by send a DELETE request to the Vultr REST API
     *
     * @param idServer the id of the server to stop
     * @throws IOException if an error occurs while stopping the server
     */
    private void stopServer(String idServer) throws IOException, NoSuchAlgorithmException {
        String query = "https://eu.api.ovh.com/1.0/cloud/project/" + this.projectId + "/instance/" + idServer;
        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = this.calculateOvhSignature(this.applicationSecret, this.consumerKey, "DELETE", query, "", timestamp);
        Request.delete(query)
                .addHeader("X-Ovh-Application", this.applicationKey)
                .addHeader("X-Ovh-Consumer", this.consumerKey)
                .addHeader("X-Ovh-Signature", signature)
                .addHeader("X-Ovh-Timestamp", String.valueOf(timestamp))
                .execute();
    }

    /**
     * Get instances list
     *
     * @return a map of instances id and their public IP
     * @throws IOException if an error occurs while getting the instances list
     */
    private Map<String, String> getInstancesList() throws IOException, NoSuchAlgorithmException {
        String query = "https://eu.api.ovh.com/1.0/cloud/project/" + this.projectId + "/instance";
        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = this.calculateOvhSignature(this.applicationSecret, this.consumerKey, "GET", query, "", timestamp);
        final Response response = Request.get(query)
                .addHeader("X-Ovh-Application", this.applicationKey)
                .addHeader("X-Ovh-Consumer", this.consumerKey)
                .addHeader("X-Ovh-Signature", signature)
                .addHeader("X-Ovh-Timestamp", String.valueOf(timestamp))
                .execute();
        String jsonResponse = response.returnContent().asString();
        JSONArray instances = new JSONArray(jsonResponse);
        Map<String, String> serverList = new HashMap<>();
        for (int i = 0; i < instances.length(); i++) {
            JSONObject instance = instances.getJSONObject(i);
            String ip = getAddressServer(instance);
            String id = instance.getString("id");
            serverList.put(id, ip);
        }
        return serverList;
    }

    public String getAddressServer(JSONObject jsonObject) {
        final List<JSONObject> ipAddresses = new ArrayList<>();
        jsonObject.getJSONArray("ipAddresses").forEach(o -> ipAddresses.add((JSONObject) o));
        return ipAddresses.get(0).getString("ip");
    }

    private String calculateOvhSignature(String applicationSecret, String consumerKey, String method, String query, String body, long timestamp) throws NoSuchAlgorithmException {
        String data = applicationSecret + "+" + consumerKey + "+" + method + "+" + query + "+" + body + "+" + timestamp;

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(data.getBytes());

        return "$1$" + this.bytesToHex(hash);
    }


    private String bytesToHex(byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }


    /**
     * Get the public IP of the minecraft server
     *
     * @return the public IP of the minecraft server
     * @throws IOException if an error occurs while getting the public IP
     */
    private String getPublicAddress() throws IOException {
        try {
            Process process = Runtime.getRuntime().exec("curl ifconfig.me");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String publicIp = reader.readLine();
            process.waitFor();
            return publicIp;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
