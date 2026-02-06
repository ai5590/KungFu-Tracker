package com.kungfu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kungfu.model.ExerciseMeta;
import com.kungfu.model.SectionMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KungFuApplicationTests {

    @TempDir
    static Path tempDir;

    static Path dataDir;
    static Path usersFile;

    @Autowired
    MockMvc mvc;

    static ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        dataDir = tempDir.resolve("data");
        usersFile = tempDir.resolve("users.txt");
        registry.add("app.data-dir", () -> dataDir.toString());
        registry.add("app.users-file", () -> usersFile.toString());
    }

    @BeforeAll
    static void setup() throws IOException {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // 1) TreeServiceTest
    @Test
    @Order(1)
    @WithMockUser
    void testTreeReturnsCorrectNodes() throws Exception {
        mvc.perform(get("/api/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].nodeType", is("SECTION")))
                .andExpect(jsonPath("$[0].name", is("KungFu")))
                .andExpect(jsonPath("$[0].children", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].children[0].name", is("Basics")))
                .andExpect(jsonPath("$[0].children[0].nodeType", is("SECTION")))
                .andExpect(jsonPath("$[0].children[0].children[0].nodeType", is("EXERCISE")));
    }

    // 2) ExerciseReadTest
    @Test
    @Order(2)
    @WithMockUser
    void testGetExerciseReturnsTextAndNotes() throws Exception {
        mvc.perform(get("/api/exercises").param("path", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("HorseStance")))
                .andExpect(jsonPath("$.text", is("Demo text")))
                .andExpect(jsonPath("$.notes", is("Demo notes")))
                .andExpect(jsonPath("$.files", hasSize(0)));
    }

    // 3) ExerciseUpdateTextTest
    @Test
    @Order(3)
    @WithMockUser
    void testUpdateTextWritesToExerciseJson() throws Exception {
        mvc.perform(put("/api/exercises/text")
                        .param("path", "KungFu/Basics/HorseStance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Updated description\"}"))
                .andExpect(status().isOk());

        Path exerciseJson = dataDir.resolve("KungFu/Basics/HorseStance/exercise.json");
        ExerciseMeta meta = mapper.readValue(exerciseJson.toFile(), ExerciseMeta.class);
        Assertions.assertEquals("Updated description", meta.getText());
        Assertions.assertNotNull(meta.getUpdatedAt());
    }

    // 4) NotesUpdateTest
    @Test
    @Order(4)
    @WithMockUser
    void testUpdateNotesWritesToFile() throws Exception {
        mvc.perform(put("/api/exercises/notes")
                        .param("path", "KungFu/Basics/HorseStance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Updated notes content\"}"))
                .andExpect(status().isOk());

        String notes = Files.readString(dataDir.resolve("KungFu/Basics/HorseStance/notes.md"), StandardCharsets.UTF_8);
        Assertions.assertEquals("Updated notes content", notes);
    }

    // 5) UploadFileTest
    @Test
    @Order(5)
    @WithMockUser
    void testUploadFilePlacesInMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "test.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("exercisePath", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk());

        Assertions.assertTrue(Files.exists(dataDir.resolve("KungFu/Basics/HorseStance/media/test.txt")));

        mvc.perform(get("/api/exercises").param("path", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.files[0].fileName", is("test.txt")));
    }

    // 6) DeleteFileTest
    @Test
    @Order(6)
    @WithMockUser
    void testDeleteFileRemovesIt() throws Exception {
        Path mediaFile = dataDir.resolve("KungFu/Basics/HorseStance/media/todelete.txt");
        Files.createDirectories(mediaFile.getParent());
        Files.writeString(mediaFile, "temp");

        Assertions.assertTrue(Files.exists(mediaFile));

        mvc.perform(delete("/api/files")
                        .param("exercisePath", "KungFu/Basics/HorseStance")
                        .param("fileName", "todelete.txt"))
                .andExpect(status().isOk());

        Assertions.assertFalse(Files.exists(mediaFile));
    }

    // 7) PathTraversalProtectionTest
    @Test
    @Order(7)
    @WithMockUser
    void testPathTraversalIsBlocked() throws Exception {
        mvc.perform(get("/api/exercises").param("path", "../../etc/passwd"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/files/stream")
                        .param("exercisePath", "KungFu/Basics/HorseStance")
                        .param("fileName", "../../etc/passwd"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/exercises").param("path", "/etc/passwd"))
                .andExpect(status().isBadRequest());
    }

    // 8) RangeStreamingTest
    @Test
    @Order(8)
    @WithMockUser
    void testRangeRequestReturns206() throws Exception {
        byte[] bigContent = new byte[5 * 1024 * 1024];
        for (int i = 0; i < bigContent.length; i++) bigContent[i] = (byte)(i % 256);
        Path mediaFile = dataDir.resolve("KungFu/Basics/HorseStance/media/big.mp4");
        Files.createDirectories(mediaFile.getParent());
        Files.write(mediaFile, bigContent);

        mvc.perform(get("/api/files/stream")
                        .param("exercisePath", "KungFu/Basics/HorseStance")
                        .param("fileName", "big.mp4")
                        .header("Range", "bytes=0-1023"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", startsWith("bytes 0-1023/")))
                .andExpect(header().string("Content-Length", "1024"));
    }

    // 9) AuthTest
    @Test
    @Order(9)
    void testUnauthenticatedAccessRedirectsToLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/api/tree"))
                .andExpect(status().is3xxRedirection());
    }
}
