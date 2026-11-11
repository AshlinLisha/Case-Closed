# Case Closed? - Detective Mystery (Java + Spring Boot)

A web-based detective game. Java (Spring Boot) holds the game rules; HTML/CSS/JavaScript is the interface.

## Run it in VS Code
1. Install JDK 17 or newer and the VS Code extensions "Extension Pack for Java" and "Spring Boot Extension Pack".
2. Open the `case-closed` folder in VS Code (File > Open Folder).
3. Open `CaseClosedApplication.java` and click **Run** above `main`, or in a terminal run `mvn spring-boot:run`
   (needs Maven; the Initializr tip below gives you a wrapper instead).
4. Open http://localhost:8080 in your browser.

Tip: no Maven installed? Generate an empty project at start.spring.io (Maven, Java, Jar, Spring Web),
then copy this project's `src/` folder and `pom.xml` over it. It comes with `mvnw`, so run `./mvnw spring-boot:run` (`mvnw spring-boot:run` on Windows).

## Structure
- `model/` - CaseFile, Suspect, Clue (records loaded from `data/cases.json`) and GameProgress (saved to `saves/progress.json`)
- `service/GameService` - all game rules: unlock logic, contradictions, scoring, saving
- `controller/GameController` - REST endpoints only
- `static/` - the four screens, `css/style.css`, `js/game.js`

## API
| Method | Endpoint | Purpose |
|---|---|---|
| GET | /api/game/case | Case title, briefing, progress |
| GET | /api/game/suspects | Suspects and questions (answers appear once asked) |
| POST | /api/game/suspects/{id}/questions/{qid} | Interrogate a suspect |
| GET | /api/game/clues | All clues with status |
| POST | /api/game/clues/{id}/inspect | Discover a clue |
| GET | /api/game/compare?clueId=&suspectId= | Compare a clue with a suspect |
| POST | /api/game/accuse | Final verdict `{suspectId, clueIds[]}` |
| POST | /api/game/reset | Start over |

## Solution (spoilers)
Victor Crane. Key clues: c2, c3, c4, c5, c6.
