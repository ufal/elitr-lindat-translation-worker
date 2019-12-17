package cz.cuni.mff.ufal;

import com.dslplatform.json.DslJson;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;


public class LindatTranslationClient implements Translator {

    //Hardwire the paths ignoring the fact we can get them from the api
    private enum ApiPaths {
        LANGUAGES("/languages"),
        MODELS("/models");

        private String path;

        private ApiPaths(String path){
            this.path = path;
        }

        public String toString(){
            return path;
        }
    }

    private static boolean isBlank(String string){
       if(string == null || string.isEmpty() || string.strip().isEmpty()){
           return true;
       }else {
           return false;
       }
    }

    private static final String DEFAULT_URL = "http://localhost:5000/api/v1";

    private String apiUrl;
    private HttpClient client;
    private DslJson<Object> json;
    private Set<Map.Entry<String, String>> availableLanguagePairs;
    private Set<String> availableModels;

    public LindatTranslationClient(){
        this(Optional.ofNullable(System.getenv("API_URL")).orElse(DEFAULT_URL));
    }

    public LindatTranslationClient(String apiUrl){
        System.err.println("Using API_URL=" + apiUrl);
       this.apiUrl =  apiUrl;
       this.client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        json = new DslJson<>();
    }


    public String translate(String text, String sourceLanguage, String targetLanguage){
        if(isBlank(text) || isBlank(sourceLanguage) || isBlank(targetLanguage)){
            throw new IllegalArgumentException("text, sourceLanguage and targetLanguage cannot be null.");
        }
        if(!validLangParams(sourceLanguage, targetLanguage)){
            throw new IllegalArgumentException(String.format("Translation from %s to %s is not available",
                    sourceLanguage, targetLanguage));
        }
        URI uri = URI.create(this.apiUrl + ApiPaths.LANGUAGES + String.format("/?src=%s&tgt=%s", sourceLanguage,
                targetLanguage));
        return processInputText(text, uri);
    }

    public String translate(String text, String modelName) {
        if(isBlank(text) || isBlank(modelName)){
            throw new IllegalArgumentException("Neither text nor modelName can be null.");
        }
        URI uri = URI.create(this.apiUrl + ApiPaths.MODELS + String.format("/%s", modelName));
        return processInputText(text, uri);
    }

    private Set<Map.Entry<String,String>> getLanguagePairs(Map langDef){
        URI langDefUri = URI.create(this.apiUrl).resolve((String)langDef.get("href"));
        var lang = fetch(langDefUri);
        var langName = (String)lang.get("name");
        var srcArr = processListing(lang, "_links", "sources");
        var tgtArr = processListing(lang, "_links", "targets");

        var languages = new HashSet<Map.Entry<String, String>>();
        for(Object otherLangObject : srcArr){
            var otherName = (String)((Map)otherLangObject).get("name");
            languages.add(new AbstractMap.SimpleEntry<>(otherName, langName));
        }
        for(Object otherLangObject : tgtArr){
            var otherName = (String)((Map)otherLangObject).get("name");
            languages.add(new AbstractMap.SimpleEntry<>(langName, otherName));
        }
        return languages;
    }

    @Override
    public Set<Map.Entry<String, String>> getAvailableLanguagePairs() {
        if(availableLanguagePairs == null){
            availableLanguagePairs = new HashSet<>();
            URI uri = URI.create(this.apiUrl + ApiPaths.LANGUAGES);
            var arr = fetchAndProcessListing(uri, "_links", "item");
            for(Object langObject: arr){
                Map langDef = (Map)langObject;
                var langs = getLanguagePairs(langDef);
                availableLanguagePairs.addAll(langs);
            }
        }
        return availableLanguagePairs;
    }

    @Override
    public Set<String> getAvailableModels() {
        if(availableModels == null){
            availableModels = new HashSet<>();
            URI uri = URI.create(this.apiUrl + ApiPaths.MODELS);
            var arr = fetchAndProcessListing(uri, "_links", "item");
            for(Object langObject: arr){
                Map langDef = (Map)langObject;
                String modelName = (String)langDef.get("name");
                availableModels.add(modelName);
            }
        }
        return availableModels;
    }

    private String processInputText(String text, URI uri){
        try {
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString("input_text=" + URLEncoder.encode(text,
                    "UTF-8"));
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(body)
                    .uri(uri)
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .setHeader("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .build();
            //String resp = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            //return resp;
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            Iterator<String> lines = json.iterateOver(String.class, response.body(), new byte[1024]);
            StringBuilder sb = new StringBuilder();
            while(lines.hasNext()){
                sb.append(lines.next());
                sb.append(' ');
            }
            return sb.toString().replace("\\n ", "\\n").stripTrailing();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "";
    }

    private Map fetch(URI uri){
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .setHeader("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return json.deserialize(Map.class, response.body(), new byte[1024]);
        }catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashMap();

    }

    private List<Object> processListing(Map result, String firstKey, String secondKey){
        if(!result.isEmpty()) {
            var linksObject = (Map) result.get(firstKey);
            return (List) linksObject.get(secondKey);
        }else {
            return new ArrayList<>();
        }
    }

    private List<Object> fetchAndProcessListing(URI uri, String firstKey, String secondKey){
        var result = fetch(uri);
        return processListing(result, firstKey, secondKey);
    }

    private boolean validLangParams(String source, String target){
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>(source, target);
        if(getAvailableLanguagePairs().contains(entry)){
            return true;
        }
        return false;
    }
}
