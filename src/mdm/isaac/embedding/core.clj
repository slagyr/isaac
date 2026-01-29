(ns mdm.isaac.embedding.core
  (:require [mdm.isaac.config :as config]))

(defmulti text-embedding
  "Generate an embedding vector for the given text using the specified provider.

   Returns a vector of floats representing the embedding.  The dimension of the vector depends on the implementation."
  (fn [_text] (-> config/active :embedding :impl)))

(defmulti dimensions
  "Returns the number of dimensions for embeddings from the current provider."
  (fn [] (-> config/active :embedding :impl)))
