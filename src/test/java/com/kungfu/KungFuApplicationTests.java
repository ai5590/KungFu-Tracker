package com.kungfu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kungfu.model.ExerciseMeta;
import com.kungfu.model.UsersData;
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

    @Test
    @Order(1)
    @WithMockUser(roles = {"USER", "EDITOR"})
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

    @Test
    @Order(2)
    @WithMockUser(roles = {"USER", "EDITOR"})
    void testGetExerciseReturnsTextAndNotes() throws Exception {
        mvc.perform(get("/api/exercises").param("path", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("HorseStance")))
                .andExpect(jsonPath("$.files", hasSize(0)));
    }

    @Test
    @Order(3)
    @WithMockUser(roles = {"USER", "EDITOR"})
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

    @Test
    @Order(4)
    @WithMockUser(roles = {"USER", "EDITOR"})
    void testUpdateNotesWritesToFile() throws Exception {
        mvc.perform(put("/api/exercises/notes")
                        .param("path", "KungFu/Basics/HorseStance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Updated notes content\"}"))
                .andExpect(status().isOk());

        String notes = Files.readString(dataDir.resolve("KungFu/Basics/HorseStance/notes.md"), StandardCharsets.UTF_8);
        Assertions.assertEquals("Updated notes content", notes);
    }

    @Test
    @Order(5)
    @WithMockUser(roles = {"USER", "EDITOR"})
    void testUploadFilePlacesInMedia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "test.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("exercisePath", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk());

        Assertions.assertTrue(Files.exists(dataDir.resolve("KungFu/Basics/HorseStance/media/test.txt")));

        mvc.perform(get("/api/exercises").param("path", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.files[?(@.fileName=='test.txt')].description", hasItem("")));
    }

    @Test
    @Order(6)
    @WithMockUser(roles = {"USER", "EDITOR"})
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

    @Test
    @Order(7)
    @WithMockUser(roles = {"USER", "EDITOR"})
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

    @Test
    @Order(8)
    @WithMockUser(roles = {"USER", "EDITOR"})
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

    @Test
    @Order(9)
    void testUnauthenticatedAccessRedirectsToLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/tree"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @Order(10)
    @WithMockUser(username = "ai", roles = {"USER", "EDITOR", "ADMIN"})
    void testMeEndpointReturnsFlags() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login", is("ai")))
                .andExpect(jsonPath("$.admin", is(true)))
                .andExpect(jsonPath("$.canEdit", is(true)));
    }

    @Test
    @Order(11)
    @WithMockUser(roles = {"USER"})
    void testReadOnlyUserCannotWrite() throws Exception {
        mvc.perform(get("/api/tree")).andExpect(status().isOk());
        mvc.perform(get("/api/exercises").param("path", "KungFu/Basics/HorseStance")).andExpect(status().isOk());

        mvc.perform(post("/api/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentPath\":\"KungFu\",\"title\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/exercises/text")
                        .param("path", "KungFu/Basics/HorseStance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"nope\"}"))
                .andExpect(status().isForbidden());

        MockMultipartFile file = new MockMultipartFile("files", "nope.txt", "text/plain", "nope".getBytes());
        mvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("exercisePath", "KungFu/Basics/HorseStance"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/files")
                        .param("exercisePath", "KungFu/Basics/HorseStance")
                        .param("fileName", "test.txt"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    @WithMockUser(roles = {"USER"})
    void testNonAdminCannotAccessAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"x\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    @WithMockUser(roles = {"USER", "EDITOR", "ADMIN"})
    void testAdminCanManageUsers() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"testuser\",\"password\":\"testpw\",\"admin\":false,\"canEdit\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='testuser')].canEdit", hasItem(true)));

        mvc.perform(put("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"testuser\",\"canEdit\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='testuser')].canEdit", hasItem(false)));

        mvc.perform(get("/api/admin/users/password").param("login", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password", is("testpw")));

        mvc.perform(delete("/api/admin/users").param("login", "testuser"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(14)
    @WithMockUser(roles = {"USER", "EDITOR", "ADMIN"})
    void testCannotDeleteLastAdmin() throws Exception {
        mvc.perform(delete("/api/admin/users").param("login", "ai"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(15)
    @WithMockUser(username = "ai", roles = {"USER", "EDITOR", "ADMIN"})
    void testChangePassword() throws Exception {
        mvc.perform(post("/api/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"newpw\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"1\",\"newPassword\":\"newpw\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"newpw\",\"newPassword\":\"1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(16)
    @WithMockUser(roles = {"USER", "EDITOR"})
    void testFileDescriptionWorkflow() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "desc_test.txt", "text/plain", "data".getBytes());
        mvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("exercisePath", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/exercises").param("path", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[?(@.fileName=='desc_test.txt')].description", hasItem("")));

        mvc.perform(put("/api/files/description")
                        .param("exercisePath", "KungFu/Basics/HorseStance")
                        .param("fileName", "desc_test.txt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Test description\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/exercises").param("path", "KungFu/Basics/HorseStance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[?(@.fileName=='desc_test.txt')].description", hasItem("Test description")));
    }

    @Test
    @Order(17)
    void testUsersMigrationFromTxt() throws Exception {
        Path usersJson = usersFile.getParent().resolve("users.json");
        Assertions.assertTrue(Files.exists(usersJson));
        UsersData data = mapper.readValue(usersJson.toFile(), UsersData.class);
        Assertions.assertFalse(data.getUsers().isEmpty());
        Assertions.assertTrue(data.getUsers().stream().anyMatch(u -> u.getLogin().equals("ai") && u.isAdmin() && u.isCanEdit()));
    }
}
