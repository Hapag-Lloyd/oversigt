package com.hlag.oversigt.web;

import static com.hlag.oversigt.test.UndertowHelper.createFormData;
import static com.hlag.oversigt.test.UndertowHelper.createHttpExchange;
import static com.hlag.oversigt.test.UndertowHelper.createHttpExchangeWithQueryParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardColorScheme;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.MailSender;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;

@RunWith(MockitoJUnitRunner.class)
public class DashboardConfigurationHandlerTest {
	private static final Set<String> VALID_USERNAMES = new HashSet<>(Arrays.asList("USER1", "USER2"));

	private static Dashboard createValidDashboard() {
		return new Dashboard("TEST",
				"TEST",
				true,
				1920,
				1080,
				15,
				Color.Black,
				DashboardColorScheme.COLORED,
				Color.White,
				Color.LightGray,
				Arrays.asList("USER1"),
				Collections.emptyList());
	}

	@Mock
	private Authenticator authenticator;
	@Mock
	private DashboardController dashboardController;
	@Mock
	private HttpServerExchangeHandler httpServerExchangeHelper;
	@Mock
	private JsonUtils json;
	@Mock
	private MailSender mailSender;

	@Captor
	private ArgumentCaptor<Collection<String>> addressCaptor;

	@InjectMocks
	private DashboardConfigurationHandler dashboardConfigurationHandler;

	@Before
	public void setup() throws IOException {
		when(authenticator.isUsernameValid(ArgumentMatchers.anyString())).thenReturn(false);
		VALID_USERNAMES.forEach(s -> when(authenticator.isUsernameValid(s)).thenReturn(true));

		Principal principal = Mockito.mock(Principal.class);
		when(httpServerExchangeHelper.getPrincipal(ArgumentMatchers.any())).thenReturn(Optional.of(principal));
		// when(httpServerExchangeHelper.getFormData(Mockito.any())).thenCallRealMethod();
		// when(httpServerExchangeHelper.param(Mockito.any(),
		// Mockito.anyString())).thenCallRealMethod();
		when(httpServerExchangeHelper.maybeParam(ArgumentMatchers.any(), ArgumentMatchers.anyString())).thenCallRealMethod();
		when(httpServerExchangeHelper.query(ArgumentMatchers.any(), ArgumentMatchers.anyString())).thenCallRealMethod();
		// when(httpServerExchangeHelper.query(Mockito.any(), Mockito.anyString(),
		// Mockito.any(), Mockito.any())) .thenCallRealMethod();
	}

	@Test
	public void shouldReturnOkAndTrue_whenCallingCheckUsername_givenValidUsername() {
		// given
		final ActionResponse expected = ActionResponse.okJson(true);
		final HttpServerExchange exchange = createHttpExchange();
		final FormData formData = createFormData("username", "USER1");

		// then
		final ActionResponse result = dashboardConfigurationHandler.doAction_checkUsername(exchange, formData);

		// that
		assertThat(result).isEqualToComparingFieldByField(expected);
	}

	@Test
	public void shouldReturnOkAndFalse_whenCallingCheckUsername_givenInvalidUsername() {
		// given
		final ActionResponse expected = ActionResponse.okJson(false);
		final HttpServerExchange exchange = createHttpExchange();
		final FormData formData = createFormData("username", "ABC");

		// then
		final ActionResponse result = dashboardConfigurationHandler.doAction_checkUsername(exchange, formData);

		// that
		assertThat(result).isEqualToComparingFieldByField(expected);
	}

	@Test
	public void shouldSendMailToUser_whenSettingOwners_givenValidAdditionalOwner() {
		// given
		final Dashboard dashboard = createValidDashboard();
		final ActionResponse expected = ActionResponse.okJson("USER1,USER2");
		final HttpServerExchange exchange = createHttpExchangeWithQueryParameters("dashboard", dashboard.getId());
		final FormData formData = createFormData("usernames", "USER1,USER2");
		final Set<String> expectedMailSet = new HashSet<>(Arrays.asList("USER2"));
		final Set<String> expectedListOfOwners = new HashSet<>(Arrays.asList("USER1", "USER2"));

		when(dashboardController.getDashboard(dashboard.getId())).thenReturn(dashboard);

		// then
		ActionResponse result = dashboardConfigurationHandler.doAction_setOwners(exchange, formData);
		Mockito.verify(mailSender)
				.sendPermissionsReceived(ArgumentMatchers.any(), addressCaptor.capture(), ArgumentMatchers.any(), ArgumentMatchers.any());

		// that
		assertThat(result).isEqualToComparingFieldByField(expected);
		assertThat(dashboard.getOwners()).isEqualTo(expectedListOfOwners);
		assertThat(dashboard.getEditors()).isEmpty();
		assertThat(addressCaptor.getValue()).isEqualTo(expectedMailSet);
	}

	@Test
	public void shouldSendMailToUser_whenSettingEditors_givenValidAdditionalEditor() {
		// given
		final Dashboard dashboard = createValidDashboard();
		final ActionResponse expected = ActionResponse.okJson("USER1,USER2");
		final HttpServerExchange exchange = createHttpExchangeWithQueryParameters("dashboard", dashboard.getId());
		final FormData formData = createFormData("usernames", "USER1,USER2");
		final Set<String> expectedMailSet = new HashSet<>(Arrays.asList("USER1", "USER2"));
		final Set<String> expectedListOfOwners = new HashSet<>(Arrays.asList("USER1"));
		final Set<String> expectedListOfEditors = new HashSet<>(Arrays.asList("USER1", "USER2"));

		when(dashboardController.getDashboard(dashboard.getId())).thenReturn(dashboard);

		// then
		ActionResponse result = dashboardConfigurationHandler.doAction_setEditors(exchange, formData);
		Mockito.verify(mailSender)
				.sendPermissionsReceived(ArgumentMatchers.any(), addressCaptor.capture(), ArgumentMatchers.any(), ArgumentMatchers.any());

		// that
		assertThat(result).isEqualToComparingFieldByField(expected);
		assertThat(dashboard.getOwners()).isEqualTo(expectedListOfOwners);
		assertThat(dashboard.getEditors()).isEqualTo(expectedListOfEditors);
		assertThat(addressCaptor.getValue()).isEqualTo(expectedMailSet);
	}

	//	@Test
	//	public void testHandleRequest() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDashboardConfigurationHandler() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_addWidget() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_updateWidget() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_updateWidgetPositions() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_deleteWidget() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_updateDashboard() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_deleteDashboard() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_reloadDashboard() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_enableWidget() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_disableWidget() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_checkUsername() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_setOwners() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}
	//
	//	@Test
	//	public void testDoAction_setEditors() throws Exception {
	//		throw new RuntimeException("not yet implemented");
	//	}

}
