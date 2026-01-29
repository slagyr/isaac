# Isaac

## Isaac Asimov's Three Laws of Robotics:
 1. A robot may not injure a human being or, through inaction, allow a human being to come to harm.
 2. A robot must obey the orders given to it by human beings, except where such orders would conflict with the First Law.
 3. A robot must protect its own existence as long as such protection does not conflict with the First or Second Law.


## Setup
 * Install Ollama
   * `ollama pull embeddinggemma`
   * `ollama pull minstral`
 * Postgresql: 
   * `brew install postgresql@17`
   * `brew install pgvector`
   * `createdb isaac`
   * `createdb isaac-test`
   * `psql -U postgres -d isaac-test -c "CREATE EXTENSION IF NOT EXISTS vector;"`
 * 
