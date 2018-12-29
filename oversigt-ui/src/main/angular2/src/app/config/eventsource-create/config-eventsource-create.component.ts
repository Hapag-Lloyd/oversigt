import { Component, OnInit } from '@angular/core';
import { EventSourceService, EventSourceInfo } from 'src/oversigt-client';
import { Router } from '@angular/router';
import { NotificationService } from 'src/app/notification.service';
import { getLinkForEventSource } from 'src/app/app.component';

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
  ) { }

  ngOnInit() {
    this.ess.listAvailableEventSources().subscribe(
      list => {
        this.eventSourceInfos = list.sort((a, b) => a.name.toLowerCase() > b.name.toLowerCase() ? 1 : -1);
        this.filter();
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
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
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  showEventSourceInfo(key: string) {
    this.notification.warning('Not yet implemented.');
  }

}
