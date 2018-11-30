import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-eventsource-button',
  templateUrl: './eventsource-button.component.html',
})
export class EventsourceButtonComponent implements OnInit {
  @Input() eventSourceId: string;
  @Input() showAsText = false;

  constructor() { }

  ngOnInit() {
  }

}
