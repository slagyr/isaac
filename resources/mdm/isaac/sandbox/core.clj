(ns mdm.isaac.sandbox.core
  (:require [mdm.isaac.server.layouts :as layouts]
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.apron.util :as util]
            [clojure.string :as str]))

(defn handler [request]
  (let [ns-name  (-> (:uri request)
                     (subs 1)
                     (str/replace "/" "."))
        var-name (str "mdm.isaac." ns-name "/render")
        var-sym  (symbol var-name)]
    (log/info "Sandbox handler attempting var-name: " var-name)
    (if-let [render-var (util/var-value var-sym)]
      (layouts/default (render-var))
      (do
        (log/info "\t no handler found.  Delegating to client side.")
        (layouts/web-rich-client request)))))

(defn sandbox-page-names []
  (let [sandbox-files    (util/clj-files-in "resource/mdm/isaac/sandbox")
        sandbox-ns-names (map util/filename->ns sandbox-files)
        short-names      (map #(str/replace % "resources.mdm.issac.sandbox." "") sandbox-ns-names)]
    (sort (remove #{"spec"} short-names))))

(defn page-section [page]
  (let [parts  (str/split page #"\.")
        prefix (str/join "." (butlast parts))]
    (or prefix page)))

(defn index [_]
  (layouts/static
    [:section.margin-top-plus-4.margin-bottom-plus-4
     [:div.container.width-750
      [:h1.margin-bottom-plus-1 "Sandbox Pages"]
      [:table.striped
       [:tbody
        (ccc/for-all [[section pages] (sort-by first (group-by page-section (sandbox-page-names)))]
          [:tr {:key section}
           [:td section]
           [:td
            [:ol
             (ccc/for-all [page pages]
               [:li {:key page}
                [:a {:href (str "/sandbox/" page)} page]])]]])]]]]))
