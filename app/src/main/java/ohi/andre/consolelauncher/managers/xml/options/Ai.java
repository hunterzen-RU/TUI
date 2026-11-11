package ohi.andre.consolelauncher.managers.xml.options;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;

public enum Ai implements XMLPrefsSave {

    ai_enabled {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String info() {
            return "Enable or disable AI features";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }
    },
    ai_mode {
        @Override
        public String defaultValue() {
            return "global";
        }

        @Override
        public String info() {
            return "AI Engine mode: 'global' for Google Gemini, 'local' for your own LLM server";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }
    },
    google_api_key {
        @Override
        public String defaultValue() {
            return "";
        }

        @Override
        public String info() {
            return "Your Google Gemini API Key (used for 'global' mode)";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }
    },
    google_model {
        @Override
        public String defaultValue() {
            return "gemini-1.5-flash";
        }

        @Override
        public String info() {
            return "Gemini model to use";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }
    },
    local_url {
        @Override
        public String defaultValue() {
            return "http://localhost:11434/v1/chat/completions";
        }

        @Override
        public String info() {
            return "The API endpoint for your local LLM server (OpenAI compatible)";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }
    },
    local_model {
        @Override
        public String defaultValue() {
            return "llama3";
        }

        @Override
        public String info() {
            return "The model name for your local LLM";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }
    },
    system_prompt {
        @Override
        public String defaultValue() {
            return "You are a helpful T-UI assistant. Keep responses concise and terminal-friendly.";
        }

        @Override
        public String info() {
            return "The system personality for the AI";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }
    };

    @Override
    public XMLPrefsElement parent() {
        return XMLPrefsManager.XMLPrefsRoot.AI;
    }

    @Override
    public String label() {
        return name();
    }

    @Override
    public String[] invalidValues() {
        return null;
    }

    @Override
    public String getLowercaseString() {
        return label();
    }

    @Override
    public String getString() {
        return label();
    }
}
