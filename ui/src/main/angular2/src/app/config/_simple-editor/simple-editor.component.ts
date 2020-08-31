import { Component, OnInit, Input, TemplateRef } from '@angular/core';
import { AbstractValueAccessor, MakeProvider } from 'src/app/_editor/abstract-value-accessor';

@Component({
  selector: 'app-simple-editor',
  templateUrl: './simple-editor.component.html',
  styleUrls: ['./simple-editor.component.css'],
  providers: [MakeProvider(SimpleEditorComponent)]
})
export class SimpleEditorComponent extends AbstractValueAccessor implements OnInit {
  @Input() id: string;
  @Input() title: string;
  @Input() description: string = null;
  @Input() readOnly = false;
  @Input() inputType = 'text';

  ngOnInit() {
  }

  isArray(): boolean {
    return this.value instanceof Array;
  }

}
