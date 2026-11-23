package com.example.QuanLyChoThueXeDien.license;
import com.example.QuanLyChoThueXeDien.auth.services.CustomUserDetailsService;
import com.example.QuanLyChoThueXeDien.config.SecurityConfig;
import com.example.QuanLyChoThueXeDien.license.controllers.DrivingLicenseController;
import com.example.QuanLyChoThueXeDien.license.services.DrivingLicenseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.beans.factory.annotation.Autowired;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = DrivingLicenseSecurityTests.Config.class)
class DrivingLicenseSecurityTests {
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, DrivingLicenseController.class})
    static class Config {
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean DrivingLicenseService licenses() { return mock(DrivingLicenseService.class); }
        @Bean org.thymeleaf.spring6.view.ThymeleafViewResolver views() {
            var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/"); resolver.setSuffix(".html");
            resolver.setCharacterEncoding("UTF-8");
            var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
            views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
            return views;
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired DrivingLicenseService service;
    MockMvc mvc;
    @BeforeEach void setup() { reset(service); mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void anonymousCannotReadLicenseImage() throws Exception {
        mvc.perform(get("/profile/license/image")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(service);
    }
    @Test void customerCannotAccessStaffPagesOrImages() throws Exception {
        mvc.perform(get("/staff/licenses").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/licenses/1/image").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void adminHasNoStaffApprovalPermission() throws Exception {
        mvc.perform(post("/staff/licenses/1").with(user("admin").roles("ADMIN")).with(csrf())
            .param("decision","Đã xác minh").param("expectedImage","image")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void csrfRequiredForDecision() throws Exception {
        mvc.perform(post("/staff/licenses/1").with(user("staff").roles("STAFF"))
            .param("decision","Đã xác minh").param("expectedImage","image")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void staffCannotUseCustomerSubmission() throws Exception {
        mvc.perform(post("/profile/license").with(user("staff").roles("STAFF")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void imageUsesAuthenticatedCustomerNotQueryEmail() throws Exception {
        when(service.ownImage("customer")).thenReturn(new DrivingLicenseService.ImageData(new byte[]{1}, "image/png"));
        mvc.perform(get("/profile/license/image").param("email","other").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store, private"));
        verify(service).ownImage("customer"); verify(service,never()).ownImage("other");
    }
    @Test void customerPageRendersFormAndCsrf() throws Exception {
        mvc.perform(get("/profile/license").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("multipart/form-data")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    }
    @Test void invalidSubmissionRendersErrors() throws Exception {
        mvc.perform(multipart("/profile/license").with(user("customer").roles("CUSTOMER")).with(csrf()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Vui lòng nhập số GPLX")));
        verify(service, never()).submit(anyString(), any());
    }
    @Test void staffDetailRendersCurrentVersionAndImage() throws Exception {
        var license = new com.example.QuanLyChoThueXeDien.license.entities.DrivingLicense();
        var customer = new com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile();
        var account = new com.example.QuanLyChoThueXeDien.auth.entities.Account();
        account.setEmail("customer@example.com"); customer.setAccount(account); customer.setFullName("Customer");
        license.setCustomer(customer); license.setLicenseId(1); license.setImagePath("current.png");
        license.setVerificationStatus("Chưa xác minh"); license.setLicenseClass("B");
        license.setIssueDate(java.time.LocalDate.of(2020,1,1)); license.setExpiryDate(java.time.LocalDate.of(2030,1,1));
        when(service.detail(1,"staff")).thenReturn(license);
        mvc.perform(get("/staff/licenses/1").with(user("staff").roles("STAFF"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("current.png")));
    }
}
