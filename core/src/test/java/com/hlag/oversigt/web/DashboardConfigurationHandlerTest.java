package com.hlag.oversigt.web;

import static com.hlag.oversigt.test.UndertowHelper.createFormData;
import static com.hlag.oversigt.test.UndertowHelper.createHttpExchange;
import static com.hlag.oversigt.test.UndertowHelper.createHttpExchangeWithQueryParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.model.DashboardColorScheme;
import com.hlag.oversigt.model.DashboardController;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.util.JsonUtils;
import com.hlag.oversigt.util.MailSender;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;

@RunWith(MockitoJUnitRunner.class)
public class DashboardConfigurationHandlerTest {
	private static final Set<String> VALID_USERNAMES = new HashSet<>(Arrays.asList("USER1", "USER2"));
	private static final Dashboard VALID_DASHBOARD = new Dashboard("TEST", "TEST", true, 1920, 1080, 15, Color.Black,
			DashboardColorScheme.COLORED, Color.White, Color.LightGray, Arrays.asList("USER1"),
			Collections.emptyList());

	@Mock
	private Authenticator authenticator;

	@Mock
	private DashboardController dashboardController;

	@Mock
	private JsonUtils json;

	@Mock
	private MailSender mailSender;
	@InjectMocks
	private DashboardConfigurationHandler dashboardConfigurationHandler;

	@Before
	public void setup() {
		when(authenticator.isUsernameValid(Mockito.anyString())).thenReturn(false);
		VALID_USERNAMES.forEach(s -> when(authenticator.isUsernameValid(s)).thenReturn(true));

		when(dashboardController.getDashboard(VALID_DASHBOARD.getId())).thenReturn(VALID_DASHBOARD);
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

	private void setupOwnerCheck() {

	}

	@Test
	public void shouldSendMailToUser_whenSettingOwners_givenValidAdditionalOwner() {
		// given
		ActionResponse expected = ActionResponse.okJson(true);
		final HttpServerExchange exchange = createHttpExchangeWithQueryParameters("dashboard", VALID_DASHBOARD.getId());
		FormData formData = createFormData("usernames", "USER1,USER2");

		// then
		ActionResponse result = dashboardConfigurationHandler.doAction_setOwners(exchange, formData);

		// that
		assertThat(result).isEqualToComparingFieldByField(expected);
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
