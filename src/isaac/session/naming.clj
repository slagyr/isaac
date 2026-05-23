(ns isaac.session.naming
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.naming :as naming]
    [isaac.system :as system]))

(def ^:private adjectives
  ["Calm" "Quiet" "Gentle" "Mellow" "Peaceful" "Tranquil" "Restful" "Serene"
   "Still" "Hushed" "Placid" "Soft" "Soothing" "Tender" "Mild" "Patient"
   "Composed" "Smooth" "Even" "Steady"
   "Sunny" "Bright" "Glowing" "Radiant" "Lambent" "Luminous" "Beaming" "Gilded"
   "Shining" "Vivid" "Glimmering" "Twinkling" "Lustrous" "Cheery" "Sparkling"
   "Merry" "Jolly" "Glad" "Gleeful" "Buoyant" "Mirthful" "Chipper" "Sprightly"
   "Spry" "Lively" "Bouncy" "Frisky" "Perky" "Zippy" "Joyful"
   "Bold" "Daring" "Plucky" "Steadfast" "Stout" "Stalwart" "Resolute" "Valiant"
   "Doughty" "Sturdy" "Faithful" "Loyal" "Hearty" "Gallant" "Brave"
   "Clever" "Curious" "Keen" "Sage" "Witty" "Wise" "Astute" "Inquiring"
   "Bookish" "Pondering"
   "Tidy" "Neat" "Trim" "Crisp" "Polite" "Mannerly" "Prim" "Smart"
   "Polished" "Refined"
   "Brisk" "Swift" "Nimble" "Quick" "Fleet" "Lithe" "Agile" "Prompt"
   "Speedy" "Snappy"
   "Cozy" "Snug" "Toasty" "Warm" "Cordial" "Genial" "Gracious" "Welcoming"
   "Homey" "Heartfelt"
   "Hopeful" "Trusty" "Earnest" "Sincere" "Honest" "Forthright" "Reliable"
   "Devoted" "Genuine" "Candid"
   "Stellar" "Lunar" "Astral" "Orbital" "Cosmic" "Solar" "Celestial" "Dawning"
   "Misty" "Hazy" "Drifting" "Wandering" "Roving"])

(def ^:private nouns
  ["Otter" "Badger" "Beaver" "Marten" "Quokka" "Hare" "Hedgehog" "Wombat"
   "Capybara" "Tapir" "Sloth" "Possum" "Lemur" "Vole" "Pika" "Shrew"
   "Ferret" "Mole" "Rabbit" "Squirrel" "Chipmunk" "Raccoon" "Bison" "Donkey"
   "Llama" "Alpaca" "Goat" "Sheep" "Camel" "Reindeer"
   "Falcon" "Heron" "Wren" "Sparrow" "Robin" "Finch" "Plover" "Dove"
   "Lark" "Thrush" "Egret" "Avocet" "Ibis" "Jay" "Magpie" "Tern"
   "Puffin" "Pelican" "Cardinal" "Swallow"
   "Tortoise" "Newt" "Toad" "Lizard" "Anole" "Skink" "Salamander" "Gecko"
   "Iguana" "Chameleon"
   "Dolphin" "Seal" "Manatee" "Narwhal" "Octopus" "Cuttle" "Walrus" "Beluga"
   "Porpoise" "Manta"
   "Bee" "Firefly" "Cricket" "Mantis" "Dragonfly"
   "Spruce" "Cedar" "Maple" "Willow" "Birch" "Aspen" "Oak" "Linden"
   "Ash" "Elm" "Beech" "Hawthorn" "Hazel" "Yew" "Rowan" "Fern"
   "Reed" "Moss" "Lichen" "Clover" "Holly" "Ivy" "Cypress" "Thyme" "Mint"
   "Harbor" "Cove" "Glade" "Meadow" "Marsh" "Ridge" "Vale" "Glen"
   "Dell" "Brook" "Rivulet" "Tarn" "Lagoon" "Bay" "Spring"
   "Comet" "Voyage" "Signal" "Beacon" "Orbit" "Lantern" "Compass" "Sextant"
   "Tiller" "Anchor" "Mast" "Aurora" "Pulsar"])

(defn- runtime-fs [state]
  (or (:fs state)
      (system/get :fs)
      (throw (ex-info "session.naming requires :fs" {}))))

(defn- state-dir->home [state-dir]
  (if (= ".isaac" (.getName (java.io.File. state-dir)))
    (.getParent (java.io.File. state-dir))
    state-dir))

(defn- name->id
  "Convert a display name to a session ID slug, matching the store's key format."
  [s]
  (let [slug (-> (or s "")
                 str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"^-+|-+$" ""))]
    (if (str/blank? slug) "session" slug)))

(defrecord SessionDomain [store]
  naming/NamedDomain
  (name-taken? [_ name]
    (contains? store (name->id name))))

(defn generate [strategy {:keys [state-dir store] :as state}]
  (case strategy
    :sequential
    (naming/generate (naming/->SequentialStrategy state-dir "sessions" "session-" (runtime-fs state)))
    (naming/generate (naming/->AdjectiveNounStrategy (->SessionDomain store) adjectives nouns))))

(defn strategy
  [state-dir fs*]
  (let [value (get-in (config/load-config {:home (state-dir->home state-dir) :fs fs*}) [:sessions :naming-strategy])]
    (cond
      (keyword? value) value
      (string? value)  (keyword value)
      :else            :adjective-noun)))
