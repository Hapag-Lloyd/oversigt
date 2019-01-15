import { Component, OnInit } from '@angular/core';
import { SystemService, OversigtEvent } from 'src/oversigt-client';
import { getLinkForEventSource } from 'src/app/app.component';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

export class EventItem {
  event: OversigtEvent;
  visible: boolean;

  constructor(event: OversigtEvent) {
    this.event = event;
    this.visible = false;
  }
}

@Component({
  selector: 'app-config-events',
  templateUrl: './config-events.component.html',
  styleUrls: ['./config-events.component.css']
})
export class ConfigEventsComponent implements OnInit {
  events: EventItem[] = [];
  filter = '';

  constructor(
    private ss: SystemService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.reloadEvents();
  }

  reloadEvents(): void {
    const _this = this;
    this.events = [];
    this.ss.getCachedEvents().subscribe(
      events => {
        _this.events = events.map(e => new EventItem(e));
      },
      this.errorHandler.createErrorHandler('Loading events')
    );
  }

  getEventSourceLink(id: string): string {
    return getLinkForEventSource(id);
  }
}
