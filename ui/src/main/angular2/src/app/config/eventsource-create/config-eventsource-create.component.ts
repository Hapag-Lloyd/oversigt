import { Component, OnInit } from '@angular/core';
import { EventSourceService, EventSourceInfo } from 'src/oversigt-client';
import { Router } from '@angular/router';
import { NotificationService } from 'src/app/services/notification.service';
import { getLinkForEventSource } from 'src/app/app.component';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

@Component({
  selector: 'app-config-eventsource-create',
  templateUrl: './config-eventsource-create.component.html',
  styleUrls: ['./config-eventsource-create.component.css']
})
export class ConfigEventsourceCreateComponent implements OnInit {
  eventSourceInfos: EventSourceInfo[];

  searchText = '';
  filteredEventSourceInfos: EventSourceInfo[];

  constructor(
    private router: Router,
    private ess: EventSourceService,
    private notification: NotificationService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.ess.listAvailableEventSources().subscribe(
      list => {
        this.eventSourceInfos = list.sort((a, b) => a.name.toLowerCase() > b.name.toLowerCase() ? 1 : -1);
        this.filter();
      },
      this.errorHandler.createErrorHandler('Listing available event sources'));
  }

  filter(): void {
    const lowerCaseSearchText = this.searchText.toLowerCase();
    this.filteredEventSourceInfos = this.eventSourceInfos.filter(esi => {
      return lowerCaseSearchText === ''
        || esi.name.toLowerCase().indexOf(lowerCaseSearchText) >= 0
        || esi.description.toLowerCase().indexOf(lowerCaseSearchText) >= 0;
    });
  }

  createEventSource(key: string) {
    this.notification.info('Creating event source...');
    this.ess.createInstance(key).subscribe(
      ok => {
        this.notification.success('Event source has been created.');
        this.router.navigateByUrl(getLinkForEventSource(ok.id));
      },
      this.errorHandler.createErrorHandler('Creating the event source instance'));
  }

  getImageLink(url: string): string {
    return url;
  }

}
