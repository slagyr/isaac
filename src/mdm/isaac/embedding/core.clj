(ns mdm.isaac.embedding.core)

(defmulti text-embedding
  "Generate an embedding vector for the given text using the specified provider.

   Supported providers:
   - :ollama - Uses Ollama's embedding API (requires Ollama running locally, 768 dims)
   - :onnx   - Uses DJL/ONNX in-process model (no external service, 384 dims)

   Returns a vector of floats representing the embedding."
  (fn [provider _text] provider))
