;; mutation-tested: 2026-05-06
(ns isaac.tool.web-search
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.system :as system]
    [isaac.tool.fs-bounds :as bounds]))

(def ^:private brave-search-endpoint "https://api.search.brave.com/res/v1/web/search")

(defn- web-search-config []
  (let [state-dir (system/get :state-dir)
        load-opts (if state-dir {:home (bounds/state-dir->home state-dir)} {})]
    (get-in (apply config/load-config (when (seq load-opts) [load-opts])) [:tools :web_search])))

(defn- web-search-api-key []
  (:api-key (web-search-config)))

(defn- web-search-provider []
  (or (:provider (web-search-config)) :brave))

(defn- web-search-config-error []
  {:isError true
   :error   "web_search not configured: set :tools :web_search :api_key (e.g. ${BRAVE_API_KEY})"})

(defn- brave-search-url [query num-results]
  (str brave-search-endpoint
       "?q=" (java.net.URLEncoder/encode query "UTF-8")
       "&count=" num-results))

(defn- parse-search-body [body]
  (json/parse-string body true))

(defn- search-results [body]
  (get-in body [:web :results]))

(defn- format-search-result [idx {:keys [title url description]}]
  (str idx ". " title "\n"
       "   " url "\n"
       "   " description))

(defn- format-search-results [results]
  (->> results
       (map-indexed (fn [idx result] (format-search-result (inc idx) result)))
       (str/join "\n\n")))

(defn web-search-tool
  "Search the web via a configured provider.
   Args: query, num_results."
  [args]
  (let [args        (bounds/string-key-map args)
        query       (get args "query")
        provider    (web-search-provider)
        api-key     (web-search-api-key)
        num-results (or (bounds/arg-int args "num_results" nil) 5)]
    (cond
      (str/blank? api-key)
      (web-search-config-error)

      (not= :brave provider)
      {:isError true :error (str "unsupported web_search provider: " provider)}

      :else
      (try
        (let [response (http/get (brave-search-url query num-results)
                                 {:headers {"X-Subscription-Token" api-key}
                                  :throw   false
                                  :timeout 30000})
              status   (:status response)]
          (cond
            (>= status 400)
            {:isError true :error (str "HTTP " status)}

            :else
            (let [results (-> response :body parse-search-body search-results)]
              (if (seq results)
                {:result (format-search-results (take num-results results))}
                {:result "no results"}))))
        (catch Exception e
          {:isError true :error (.getMessage e)})))))
