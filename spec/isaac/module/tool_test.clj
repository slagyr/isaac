(ns isaac.module.tool-test)

(defn handle [args]
  {:result (str "module:" (:msg args))})
