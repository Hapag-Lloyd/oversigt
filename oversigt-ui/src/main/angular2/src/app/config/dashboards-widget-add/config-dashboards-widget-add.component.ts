import { Component, OnInit } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo, DashboardWidgetService } from 'src/oversigt-client';
import { Subject, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { getLinkForDashboardWidget } from 'src/app/app.component';
import { NotificationService } from 'src/app/services/notification.service';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

@Component({
  selector: 'app-config-dashboard-widget-add',
  templateUrl: './config-dashboards-widget-add.component.html',
  styleUrls: ['./config-dashboards-widget-add.component.css']
})
export class ConfigDashboardWidgetAddComponent implements OnInit {
  private searchTerms = new Subject<string>();
  widgets$: Observable<EventSourceInstanceInfo[]>;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private eventSourceService: EventSourceService,
    private dashboardWidgetService: DashboardWidgetService,
    private notification: NotificationService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.widgets$ = this.searchTerms.pipe(
      startWith(''), // initial search value
      debounceTime(300), // wait 300ms after each keystroke before considering the term
      distinctUntilChanged(), // ignore new term if same as previous term
      switchMap((term: string) => this.eventSourceService.listInstances(term)), // let the server search
    );
  }

  search(filter: string): void {
    this.searchTerms.next(filter);
  }

  getDashboardId(): string {
    return this.route.snapshot.parent.params['dashboardId'];
  }

  addWidget(id: string): void {
    const dashboardId = this.getDashboardId();
    this.dashboardWidgetService.createWidget(dashboardId, id).subscribe(widgetDetails => {
      this.notification.success('The widget has been created.');
      this.router.navigateByUrl(getLinkForDashboardWidget(dashboardId, widgetDetails.id));
    },
    this.errorHandler.createErrorHandler('Creating the widget'));
  }
}
