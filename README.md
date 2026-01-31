# DevAI Assistant

**Purpose:** DevAI Assistant is a developer-focused tool designed to assist with file management, project analysis, and AI-driven workflows—without requiring paid AI subscriptions. It is ideal for developers who want private, offline-capable AI tools for project inspection, prompt-based code analysis, and file previews.

---

## 📌 Current Features

- **File Import & Management**  
  Upload local files into the app for preview, analysis, and structured project organization.

- **Project Tree Generation**  
  Automatically generate and display a hierarchical project tree for any imported folder or archive.

- **File Preview & Chunked Reading**  
  View large files in manageable chunks to prevent UI freezing and allow efficient navigation.

- **Copy & Export Project Trees**  
  Copy a text representation of the project tree or export it as a JSON file for external use.

- **Lightweight UI & Jetpack Compose Layouts**  
  Modern Compose-based interface optimized for readability and developer workflows.

- **Offline-Ready Architecture**  
  All file handling and project visualization works entirely offline, with optional hooks for AI-assisted analysis in the future.

---

## 🔧 Planned Features

- **Custom Personas & System Prompts**  
  Define custom AI behaviors for code review, translation, summarization, or analysis tasks.

- **Context Management & Summarization**  
  Summarize file contents or code snippets to reduce token usage and improve analysis efficiency.

- **Encrypted Local Storage**  
  Protect sensitive files and chat logs with SQLCipher + Room encryption.

- **Export/Import Functionality**  
  Save workspaces and project trees, import later, or sync with external drives.

- **Biometric Security & Privacy Controls**  
  Optional fingerprint or face authentication to access sensitive files.

- **Offline AI Integration**  
  Run lightweight AI models locally for basic analysis and summarization, minimizing costs.

---

## 💻 How to Build & Run

1. **Clone the repository**  

   ```bash
   git clone https://github.com/<username>/DevAI-Assistant.git
   cd DevAI-Assistant