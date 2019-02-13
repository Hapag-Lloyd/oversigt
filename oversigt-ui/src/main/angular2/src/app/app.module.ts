import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ToastrModule } from 'ngx-toastr';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { ClarityModule, ClrFormsNextModule } from '@clr/angular';
import { environment } from 'src/environments/environment';

import { AppComponent } from './app.component';
import { DashboardService, ApiModule, Configuration } from '../oversigt-client';
import { JsonSchemaEditorModule } from './json-schema-editor/json-schema-editor.module';
import { WelcomeComponent } from './welcome/welcome.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { HeaderComponent } from './config/_header/header.component';
import { LoginComponent } from './login/login.component';
import { SimpleEditorComponent } from './config/_simple-editor/simple-editor.component';
import { EventsourceButtonComponent } from './eventsource-button/eventsource-button.component';
import { ConfigurationComponent } from './config/main/main.component';
import { ConfigListChildcomponentsComponent } from './config/list-childcomponents/config-list-childcomponents.component';
import { ConfigPropertyComponent } from './config/property/config-property.component';
import { ConfigSystemComponent } from './config/system/config-system.component';
import { ConfigServerComponent } from './config/system-server/config-server.component';
import { ConfigThreadsComponent } from './config/system-threads/config-threads.component';
import { ConfigLogsLogfileComponent } from './config/system-logfiles/config-logs-logfile.component';
import { ConfigLogsLoggerComponent } from './config/system-loggers/config-logs-logger.component';
import { ConfigEventsComponent } from './config/system-events/config-events.component';
import { ConfigEventsourcesComponent } from './config/eventsources-main/config-eventsources.component';
import { ConfigEventsourcesListComponent } from './config/eventsources-list/config-eventsources-list.component';
import { ConfigEventsourceCreateComponent } from './config/eventsource-create/config-eventsource-create.component';
import { ConfigEventsourcesDetailsComponent } from './config/eventsources-details/config-eventsources-details.component';
import { ConfigEventsourceEditorComponent } from './config/eventsource-editor/config-eventsource-editor.component';
import { FilterEventsourceinfoPipe } from './pipes/filter-eventsourceinfo.pipe';
import { ConfigDashboardsComponent } from './config/dashboards/config-dashboards.component';
import { ConfigDashboardsEditComponent } from './config/dashboards-edit/config-dashboards-edit.component';
import { ConfigDashboardWidgetComponent } from './config/dashboards-widget/config-dashboards-widget.component';
import { ConfigDashboardWidgetAddComponent } from './config/dashboards-widget-add/config-dashboards-widget-add.component';
import { AuthGuard } from './guards/auth.guard';
import { PrettyPrintPipe } from './pipes/prettyprint.pipe';
import { FilterEventitemPipe } from './pipes/filter-eventitem.pipe';
import { FilterForRolePipe } from './pipes/filter-for-role.pipe';
import { HasRoleDirective } from './directives/role-based.directive';
import { ConfigServerConfigurationComponent } from './config/system-server-configuration/config-server-configuration.component';

const appRoutes: Routes = [
  { path: 'login',                    component: LoginComponent, },
  { path: '',                         component: ConfigurationComponent, // WelcomeComponent, // },
  // { path: 'config',                   component: ConfigurationComponent,
                                      canActivate: [AuthGuard],
                                      canActivateChild: [AuthGuard], children: [
    { path: 'dashboards',             component: ConfigDashboardsComponent, runGuardsAndResolvers: 'always', children: [
      { path: ':dashboardId',         component: ConfigDashboardsEditComponent, children: [
        { path: 'add',                component: ConfigDashboardWidgetAddComponent } ,
        { path: ':widgetId',          component: ConfigDashboardWidgetComponent } ,
      ] }
    ] },
    { path: 'dashboards/create',      component: ConfigurationComponent },
    { path: 'eventSources',           component: ConfigEventsourcesComponent, runGuardsAndResolvers: 'always', children: [
      { path: 'create',               component: ConfigEventsourceCreateComponent },
      { path: 'list',                 component: ConfigEventsourcesListComponent },
      { path: ':id',                  component: ConfigEventsourcesDetailsComponent },
    ] },
    { path: 'system',                 component: ConfigListChildcomponentsComponent, children: [
      { path: 'logfiles',             component: ConfigLogsLogfileComponent },
      { path: 'loggers',              component: ConfigLogsLoggerComponent },
      { path: 'events',               component: ConfigEventsComponent },
      { path: 'threads',              component: ConfigThreadsComponent },
      { path: 'config',               component: ConfigServerConfigurationComponent },
      { path: 'server',               component: ConfigServerComponent },
    ]},
    { path: 'properties',             component: ConfigListChildcomponentsComponent },
    { path: 'properties/:name',       component: ConfigPropertyComponent },
  ] },
  // { path: ':dashboardId',             component: DashboardComponent },
/*  { path: '**', component: PageNotFoundComponent }*/
];

export function initializeApiConfiguration(): Configuration {
  return new Configuration({
    basePath: environment.apiEndpoint,
    apiKeys: {'Authorization': environment.authorizationKey}
  });
}

@NgModule({
  declarations: [
    AppComponent,
    WelcomeComponent,
    DashboardComponent,
    ConfigurationComponent,
    LoginComponent,
    ConfigLogsLogfileComponent,
    ConfigLogsLoggerComponent,
    ConfigSystemComponent,
    ConfigServerComponent,
    ConfigPropertyComponent,
    ConfigEventsourcesComponent,
    SimpleEditorComponent,
    ConfigEventsourceCreateComponent,
    ConfigEventsourceEditorComponent,
    FilterEventsourceinfoPipe,
    HeaderComponent,
    ConfigEventsComponent,
    PrettyPrintPipe,
    FilterEventitemPipe,
    ConfigThreadsComponent,
    ConfigDashboardsComponent,
    ConfigDashboardsEditComponent,
    EventsourceButtonComponent,
    ConfigEventsourcesDetailsComponent,
    FilterForRolePipe,
    ConfigEventsourcesListComponent,
    ConfigDashboardWidgetComponent,
    ConfigDashboardWidgetAddComponent,
    ConfigListChildcomponentsComponent,
    HasRoleDirective,
    ConfigServerConfigurationComponent,
  ],
  imports: [
    RouterModule.forRoot(
      appRoutes,
      {onSameUrlNavigation: 'reload'},
      // { enableTracing: true } // <-- debugging purposes only
    ),
    BrowserModule,
    HttpClientModule,
    ApiModule.forRoot(initializeApiConfiguration),
    FormsModule,
    BrowserAnimationsModule,
    JsonSchemaEditorModule,
    ClarityModule,
    ClrFormsNextModule,
    ReactiveFormsModule,
    ToastrModule.forRoot(), // ToastrModule added
  ],
  providers: [DashboardService],
  bootstrap: [AppComponent]
})
export class AppModule { }

