(ns isaac.config.schema-contributions
  "Canonical :isaac.config/schema contribution map for the server module.
   resources/isaac-manifest.edn must stay aligned with this data.")

(def server
  {:acp {:fragment 'isaac.config.schema/acp}
   :channels {:fragment 'isaac.config.schema/channels}
   :comms {:fragment 'isaac.config.schema/field-comms}
   :crew {:fragment 'isaac.config.schema/field-crew
          :entity-dir "crew"
          :frontmatter? true
          :merge-root-entity? true
          :companion {:field :soul :mode :exclusive}}
   :cron {:fragment 'isaac.config.schema/field-cron
          :entity-dir "cron"
          :frontmatter? true
          :merge-root-entity? true
          :companion {:field :prompt :mode :required}}
   :defaults {:fragment 'isaac.config.schema/defaults}
   :gateway {:fragment 'isaac.config.schema/gateway}
   :hail {:fragment 'isaac.config.schema/hail
          :entity-dir "hail"
          :merge-root-entity? true
          :companion {:field :prompt :mode :optional}}
   :hooks {:fragment 'isaac.config.schema/hooks
           :entity-dir "hooks"
           :frontmatter? true
           :companion {:field :template :mode :required}}
   :models {:fragment 'isaac.config.schema/field-models
            :entity-dir "models"
            :merge-root-entity? true}
   :providers {:fragment 'isaac.config.schema/field-providers
               :entity-dir "providers"
               :merge-root-entity? true}
   :prompt-dir-names {:fragment 'isaac.config.schema/field-prompt-dir-names}
   :prompt-paths {:fragment 'isaac.config.schema/field-prompt-paths}
   :prefer-entity-files {:fragment 'isaac.config.schema/field-prefer-entity-files}
   :command-paths {:fragment 'isaac.config.schema/field-command-paths}
   :skill-paths {:fragment 'isaac.config.schema/field-skill-paths}
   :skill-menu-threshold {:fragment 'isaac.config.schema/field-skill-menu-threshold}
   :slash-commands {:fragment 'isaac.config.schema/slash-commands}
   :sessions {:fragment 'isaac.config.schema/sessions}
   :server {:fragment 'isaac.config.schema/server}
   :tools {:fragment 'isaac.config.schema/field-tools}
   :tz {:fragment 'isaac.config.schema/field-tz}})