(ns isaac.config.check-contributions
  "Canonical :isaac.config/check contribution map for the server module.
   resources/isaac-manifest.edn must stay aligned with this data.")

(def server
  {:comm-reserved-schema {:fn 'isaac.config.checks/check-comm-reserved-schema}
   :comms                 {:fn 'isaac.config.checks/check-comms}
   :crew-compaction       {:fn 'isaac.config.checks/check-crew-compaction}
   :manifest-refs         {:fn 'isaac.config.checks/check-manifest-refs}
   :provider-types        {:fn 'isaac.config.checks/check-provider-types}
   :resolved-providers    {:fn 'isaac.config.checks/check-resolved-providers}
   :slash-commands        {:fn 'isaac.config.checks/check-slash-commands}
   :tools                 {:fn 'isaac.config.checks/check-tools}})