(ns isaac.session.naming
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]))

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

(defn- state-dir->home [state-dir]
  (if (= ".isaac" (.getName (java.io.File. state-dir)))
    (.getParent (java.io.File. state-dir))
    state-dir))

(defn- counter-path [state-dir]
  (str state-dir "/sessions/.counter"))

(defn- read-counter [state-dir]
  (let [path (counter-path state-dir)]
    (or (some-> (when (fs/exists? path) (fs/slurp path)) str/trim parse-long)
        0)))

(defn- write-counter! [state-dir n]
  (let [path (counter-path state-dir)]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (str n))))

(defmulti generate (fn [strategy _state] strategy))

(defmethod generate :adjective-noun [_ _]
  (str (rand-nth adjectives) " " (rand-nth nouns)))

(defmethod generate :sequential [_ {:keys [state-dir store]}]
  (loop [n (inc (read-counter state-dir))]
    (let [name (str "session-" n)]
      (if (contains? store name)
        (recur (inc n))
        (do
          (write-counter! state-dir n)
          name)))))

(defmethod generate :default [_ state]
  (generate :adjective-noun state))

(defn strategy [state-dir]
  (let [value (get-in (config/load-config {:home (state-dir->home state-dir)}) [:sessions :naming-strategy])]
    (cond
      (keyword? value) value
      (string? value)  (keyword value)
      :else            :adjective-noun)))
