# IntelliJ Platform Modular Plugin Template


## Overview

This repository is a modular IntelliJ Platform plugin template. It introduces a concept of content modules as a unit of functionality that the plugin consists of. 
It also demonstrates how to use this concept to separate UI code from business logic. Not only does it help to keep the plugin code clean, but it also allows implementing features in a way they work natively in **split mode** just like in the ordinary monolithic IDE.

It packages a single plugin out of separate `shared`, `frontend`, and `backend` modules and demonstrates how to keep UI code on the frontend side while delegating stateful logic to the backend side through RPC.

## Demo Functionality

The sample plugin adds a `ModularPlugin` tool window with a chat-style UI implemented with the Compose framework.

## Module Layout

- `root project` assembles the final plugin, declares the main IntelliJ Platform dependency, enables split mode, and includes the `shared`, `frontend`, and `backend` plugin modules in the final distribution.
- `shared` contains contracts that both sides must understand: RPC interfaces, DTOs, serializers, and shared model types. Put a cross-boundary API here.
- `frontend` contains UI-only code and presentation logic: the tool window registration, Compose UI, view models, and the frontend adapter that talks to the backend via RPC.
- `backend` contains project-level services and business logic: access to project, file system, and external processes, message creation, response generation, and the RPC implementation exposed to the frontend.

## Split Mode Native Feature Architecture

The demo is intentionally split so that the UI stays frontend-only and the business logic stays backend-only.

A high-level overview of the plugin structure:
- a UI for a chat with an AI assistant natively rendered in the frontend IDE in split mode
- data transfer between the frontend and backend via RPC
- RPC implementation in the backend IDE is capable of touching any backend entities and APIs like a file system

A more detailed explanation of how it is implemented:
1. The frontend registers the tool window and creates `ChatViewModel`.
2. `ChatViewModel` depends on the frontend-facing `ChatRepositoryApi` abstraction instead of directly depending on backend services.
3. `FrontendChatRepositoryModel` implements that abstraction by calling the shared `ChatRepositoryRpcApi` and collecting the backend message `Flow`.
4. The shared module defines `ChatRepositoryRpcApi` plus the DTOs used to cross the RPC boundary.
5. The backend registers `BackendRpcApiProvider`, which exposes `BackendChatRepositoryRpcApi` as the RPC implementation.
6. `BackendChatRepositoryRpcApi` resolves the backend project from `ProjectId` and delegates to `BackendChatRepositoryModel`.
7. `BackendChatRepositoryModel` owns the mutable message list and the demo response generation logic.

This separation keeps the frontend focused on rendering, local UI state, and interaction handling, while the backend owns project-scoped state and logic that should execute on the backend side in split mode.