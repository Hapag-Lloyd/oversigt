import { Component, OnInit, Input, TemplateRef } from '@angular/core';
import { AbstractValueAccessor, MakeProvider } from 'src/app/_editor/abstract-value-accessor';

@Component({
  selector: 'app-config-eventsource-info',
  templateUrl: './config-eventsource-info.component.html',
  styleUrls: ['./config-eventsource-info.component.css'],
  providers: [MakeProvider(ConfigEventsourceInfoComponent)]
})
export class ConfigEventsourceInfoComponent extends AbstractValueAccessor implements OnInit {
  @Input() id: string;
  @Input() title: string;
  @Input() description: string = null;
  @Input() readOnly = false;
  @Input() inputType = 'text';
  @Input() secondaryInput: TemplateRef<any> = null;

  ngOnInit() {
  }

}
