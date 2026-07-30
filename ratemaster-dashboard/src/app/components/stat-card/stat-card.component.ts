import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stat-card.component.html',
  styleUrl: './stat-card.component.css'
})
export class StatCardComponent {
  @Input() title: string = '';
  @Input() value: string = '';
  @Input() trend: string = '';
  @Input() trendType: 'up' | 'down' | 'neutral' = 'neutral';
  @Input() icon: string = '';
}
