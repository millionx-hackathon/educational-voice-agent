# Educational Voice AI System

A voice-based educational AI system for rural students using Spring Boot, Spring AI, PGVector, Twilio, and Ultravox. Students can call a phone number and ask questions about their textbook content - no internet required!

## Architecture

```
┌──────────────┐         ┌──────────────┐         ┌─────────────┐
│   Student    │ ◄─────► │    Twilio    │ ◄─────► │   Spring    │
│   (Phone)    │         │   (Voice)    │         │    Boot     │
└──────────────┘         └──────────────┘         └──────┬──────┘
                                                          │
                        ┌─────────────────────────────────┼──────────────┐
                        │                                 │              │
                        ▼                                 ▼              ▼
                ┌───────────────┐              ┌──────────────┐  ┌──────────────┐
                │   Ultravox    │              │   PGVector   │  │ Google GenAI │
                │   (Voice AI)  │              │  (Postgres)  │  │  (Gemini +   │
                │               │              │              │  │  Embeddings) │
                └───────────────┘              └──────────────┘  └──────────────┘
```

## Technology Stack

- **Java 21** + **Spring Boot 3.5.9** + **Spring AI 1.1.2**
- **Google GenAI** (Gemini 2.0 Flash + text-embedding-004)
- **PostgreSQL** with **PGVector** extension
- **Twilio** for voice calls
- **Ultravox** for real-time voice AI

## Prerequisites

- Java 21+
- Docker & Docker Compose
- API Keys:
  - [Google AI Studio](https://aistudio.google.com/app/apikey) - GenAI API key
  - [Twilio Console](https://console.twilio.com) - Account SID, Auth Token, Phone Number
  - [Ultravox](https://ultravox.ai) - API key

## Quick Start

### 1. Configure Secrets

Create `src/main/resources/secrets.properties`:

```properties
# Google GenAI
spring.ai.google.genai.api-key=YOUR_GOOGLE_API_KEY

# Twilio
twilio.account-sid=YOUR_TWILIO_ACCOUNT_SID
twilio.auth-token=YOUR_TWILIO_AUTH_TOKEN
twilio.phone-number=+1234567890

# Ultravox
ultravox.api-key=YOUR_ULTRAVOX_API_KEY
```

### 2. Run the Application

```bash
./gradlew bootRun
```

This will:
- Start PostgreSQL with PGVector (via Docker Compose)
- Initialize the vector store schema
- Start the Spring Boot application on port 8080

### 3. Upload a Textbook

```bash
curl -X POST http://localhost:8080/api/textbooks/upload \
  -F "file=@your-textbook.pdf"
```

### 4. Test RAG Query

```bash
curl -X POST http://localhost:8080/api/query/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is photosynthesis?"}'
```

### 5. Setup Voice Calls (Production)

1. Install and start ngrok:
   ```bash
   ngrok http 8080
   ```

2. Configure Twilio webhook URL in your Twilio Console:
   ```
   https://your-ngrok-url.ngrok.io/api/twilio/incoming-call
   ```

3. Call your Twilio phone number and start asking questions!

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/textbooks/upload` | POST | Upload and index PDF textbook |
| `/api/textbooks/health` | GET | Health check |
| `/api/query/ask` | POST | Test RAG queries |
| `/api/query/simple` | POST | Test AI (no RAG) |
| `/api/rag/query` | POST | Ultravox tool endpoint |
| `/api/twilio/incoming-call` | POST | Twilio webhook |
| `/api/twilio/call-status` | POST | Call status updates |

## Project Structure

```
src/main/java/dev/zisan/ultravox_twilio/
├── config/
│   ├── TwilioConfig.java        # Twilio SDK initialization
│   └── HttpClientConfig.java    # OkHttp + ObjectMapper beans
├── controller/
│   ├── TextbookController.java  # PDF upload endpoint
│   ├── QueryController.java     # Test RAG queries
│   ├── RAGController.java       # Ultravox tool endpoint
│   └── TwilioWebhookController.java  # Incoming call handler
├── service/
│   ├── DocumentProcessingService.java  # PDF → chunks → vectors
│   ├── EducationRAGService.java        # RAG with Spring AI
│   └── UltravoxService.java            # Ultravox API calls
└── UltravoxTwilioApplication.java      # Main class
```

## How It Works

1. **Document Ingestion**: PDF textbooks are uploaded, chunked (1000 chars with 200 overlap), and stored as embeddings in PGVector
2. **Incoming Call**: Twilio receives the call and forwards to our webhook
3. **Voice Session**: We create an Ultravox session with RAG tool configured
4. **Voice Interaction**: Student speaks → Ultravox transcribes → calls our RAG tool → generates response → speaks back
5. **RAG Query**: QuestionAnswerAdvisor searches PGVector for relevant textbook chunks and Gemini generates the answer

## Configuration Reference

### application.properties

```properties
# Spring AI - Google GenAI
spring.ai.google.genai.chat.options.model=gemini-2.0-flash
spring.ai.google.genai.chat.options.temperature=0.3
spring.ai.google.genai.embedding.options.model=text-embedding-004

# PGVector
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.dimensions=768

# Ultravox
ultravox.api-url=https://api.ultravox.ai/api
ultravox.model=fixie-ai/ultravox
ultravox.voice=Jessica
ultravox.temperature=0.3
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| PostgreSQL connection failed | Ensure Docker is running |
| Google GenAI errors | Verify API key, check quota |
| Twilio webhooks not working | Use ngrok, verify URL in console |
| Ultravox connection issues | Check API key, network connectivity |
| RAG not finding answers | Ensure textbook is uploaded, check similarity threshold |

## License

MIT
