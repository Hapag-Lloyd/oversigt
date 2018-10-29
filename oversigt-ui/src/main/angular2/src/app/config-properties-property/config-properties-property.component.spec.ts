import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigPropertiesPropertyComponent } from './config-properties-property.component';

describe('ConfigPropertiesPropertyComponent', () => {
  let component: ConfigPropertiesPropertyComponent;
  let fixture: ComponentFixture<ConfigPropertiesPropertyComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigPropertiesPropertyComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigPropertiesPropertyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
