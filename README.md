# File Storage and Sharing System (PBL4)

This repository contains a Client-Server application designed for secure and convenient file management, storage, and distribution. It serves as the final submission for the "Operating Systems & Computer Networking Project" (PBL4).

---

## Project Details

* **Authors:** Dang Hoang Huy
* **Advisor:** Nguyen Cong Danh.
* **Institution:** Faculty of Information Technology, Da Nang University of Science and Technology.
* **Date:** December 2025.

---

## Core Capabilities

### User & File Management
* Secure authentication flow with hashed password registration and login.
* Upload local files to the centralized server and browse personal directories.
* Execute file downloads and deletions within the user's private workspace.

### Teamwork & Distribution
* Peer-to-peer file sharing capabilities with specific users.
* Group workspace creation, member management (invite/kick), and shared file handling.
* Generation of public access URLs (Public Links) featuring optional passwords and time-to-live (TTL) expiration timers.

### Advanced Mechanics
* Built-in file version control to monitor changes and rollback to older states.
* Concurrency control via Pessimistic Locking to prevent data loss when multiple users modify the same resource.
* Context-aware search engine to find files by name across personal, shared, and group environments.

---

## Technology Stack

* **Language:** Java (JDK 21).
* **Frontend:** JavaFX 21 alongside FXML for UI structuring.
* **Backend Architecture:** MVC pattern, standard Java Sockets (TCP/IP), and multi-threading for concurrent client handling.
* **Data Persistence:** MySQL Server 8.0.33 integrated via JDBC.
* **Tools:** VS Code, Maven, JavaFX Scene Builder, and MySQL Workbench.

---

## System Design

This solution implements a centralized data processing Client-Server model. The backend server, written entirely in Core Java, takes charge of business rules, multi-thread connection pooling, and resource allocation. The client is a dedicated JavaFX desktop program providing the visual interface.

While all file metadata resides in the MySQL relational database, the actual binary files are written directly to the server's local disk. The network communication relies on a custom, text-based application protocol running over standard TCP/IP sockets.
