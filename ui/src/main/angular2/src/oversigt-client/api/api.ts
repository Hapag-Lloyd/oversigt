export * from './authentication.service';
import { AuthenticationService } from './authentication.service';
export * from './dashboard.service';
import { DashboardService } from './dashboard.service';
export * from './dashboardWidget.service';
import { DashboardWidgetService } from './dashboardWidget.service';
export * from './eventSource.service';
import { EventSourceService } from './eventSource.service';
export * from './serializableValue.service';
import { SerializableValueService } from './serializableValue.service';
export * from './system.service';
import { SystemService } from './system.service';
export * from './view.service';
import { ViewService } from './view.service';
export const APIS = [AuthenticationService, DashboardService, DashboardWidgetService, EventSourceService, SerializableValueService, SystemService, ViewService];
