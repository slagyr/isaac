(ns isaac.api.logger
  (:require [isaac.logger :as impl]))

(defmacro log    [level event & kvs] `(impl/log*   ~level  ~event ~*file* ~(:line (meta &form)) ~@kvs))
(defmacro error  [event & kvs]       `(impl/log*   :error  ~event ~*file* ~(:line (meta &form)) ~@kvs))
(defmacro warn   [event & kvs]       `(impl/log*   :warn   ~event ~*file* ~(:line (meta &form)) ~@kvs))
(defmacro report [event & kvs]       `(impl/log*   :report ~event ~*file* ~(:line (meta &form)) ~@kvs))
(defmacro info   [event & kvs]       `(impl/log*   :info   ~event ~*file* ~(:line (meta &form)) ~@kvs))
(defmacro debug  [event & kvs]       `(impl/log*   :debug  ~event ~*file* ~(:line (meta &form)) ~@kvs))
(defmacro ex     [event e & kvs]     `(impl/log*   :error  ~event ~*file* ~(:line (meta &form)) (impl/ex-context ~e ~@kvs)))
