import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import en from '@angular/common/locales/en';

import { NgZorroAntdModule, NZ_I18N, en_US } from 'ng-zorro-antd';
import { AppComponent } from './app.component';
import { DashboardService, ApiModule, Configuration } from '../oversigt-client';
import { WelcomeComponent } from './welcome/welcome.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { LoginComponent } from './login/login.component';
import { ConfigurationComponent } from './config/main/main.component';
import { ConfigPropertyComponent } from './config/property/config-property.component';
import { ConfigSystemComponent } from './config/system/config-system.component';
import { ConfigThreadsComponent } from './config/system-threads/config-threads.component';
import { ConfigLogsLogfileComponent } from './config/system-logfiles/config-logs-logfile.component';
import { ConfigLogsLoggerComponent } from './config/system-loggers/config-logs-logger.component';
import { ConfigEventsComponent } from './config/system-events/config-events.component';
import { ConfigMenuComponent } from './config/_menu/config-menu.component';
import { ConfigEventsourcesComponent } from './config/eventsources-main/config-eventsources.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import { ConfigEventsourceInfoComponent } from './config/eventsource-info/config-eventsource-info.component';
import { ConfigEventsourceCreateComponent } from './config/eventsource-create/config-eventsource-create.component';
import { ConfigEventsourceEditorComponent } from './config/eventsource-editor/config-eventsource-editor.component';
import { FilterEventsourceinfoPipe } from './pipes/filter-eventsourceinfo.pipe';
import { HeaderComponent } from './config/_header/header.component';
import { PrettyPrintPipe } from './pipes/prettyprint.pipe';
import { FilterEventitemPipe } from './pipes/filter-eventitem.pipe';
import { ConfigDashboardsComponent } from './config/dashboards/config-dashboards.component';
import { ConfigDashboardsEditComponent } from './config/dashboards-edit/config-dashboards-edit.component';
import { EventsourceButtonComponent } from './eventsource-button/eventsource-button.component';
import { ConfigEventsourcesDetailsComponent } from './config/eventsources-details/config-eventsources-details.component';
import { FilterForRolePipe } from './pipes/filter-for-role.pipe';
import { environment } from 'src/environments/environment';
import { AuthGuard } from './guards/auth.guard';
import { JsonSchemaEditorModule } from './json-schema-editor/json-schema-editor.module';
import { ClarityModule, ClrFormsNextModule } from '@clr/angular';

registerLocaleData(en);

const appRoutes: Routes = [
  { path: '',                         component: WelcomeComponent },
  { path: 'login',                    component: LoginComponent },
  { path: 'config',                   component: ConfigurationComponent,
                                      canActivate: [AuthGuard],
                                      canActivateChild: [AuthGuard], children: [
    { path: 'createDashboard',        component: ConfigurationComponent },
    { path: 'dashboards',             component: ConfigDashboardsComponent, children: [
      { path: ':id',                  component: ConfigDashboardsEditComponent, children: [
        { path: ':id',                component: ConfigurationComponent }
      ] }
    ] },
    { path: 'dashboards/create',      component: ConfigurationComponent },
    { path: 'eventSources',           component: ConfigurationComponent, children: [
      { path: 'create',               component: ConfigEventsourceCreateComponent },
      { path: ':id',                  component: ConfigEventsourcesDetailsComponent },
    ] },
    { path: 'system',                 component: ConfigSystemComponent, children: [
      { path: 'logfiles',             component: ConfigLogsLogfileComponent },
      { path: 'loggers',              component: ConfigLogsLoggerComponent },
      { path: 'events',               component: ConfigEventsComponent },
      { path: 'threads',              component: ConfigThreadsComponent },
      { path: 'server',               component: ConfigurationComponent }, // TODO
    ]},
    { path: 'properties/:name',       component: ConfigPropertyComponent },
    // { path: 'properties/:name',    component: ConfigPropertiesPropertyComponent },
  ] },
  { path: ':dashboardId',             component: DashboardComponent },
/*  { path: '**', component: PageNotFoundComponent }*/
];

export function initializeApiConfiguration(): Configuration {
  return new Configuration({apiKeys: {'Authorization': environment.authorizationKey}});
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
    ConfigPropertyComponent,
    ConfigMenuComponent,
    ConfigEventsourcesComponent,
    ConfigEventsourceInfoComponent,
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
  ],
  imports: [
    RouterModule.forRoot(
      appRoutes,
      // { enableTracing: true } // <-- debugging purposes only
    ), BrowserModule, HttpClientModule,
    ApiModule.forRoot(initializeApiConfiguration),
    FormsModule,
    BrowserAnimationsModule,
    NgZorroAntdModule,
    JsonSchemaEditorModule,
    ClarityModule,
    ClrFormsNextModule,
  ],
  providers: [DashboardService, { provide: NZ_I18N, useValue: en_US }],
  bootstrap: [AppComponent]
})
export class AppModule { }

